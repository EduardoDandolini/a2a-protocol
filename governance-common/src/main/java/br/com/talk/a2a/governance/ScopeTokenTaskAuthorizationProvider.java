package br.com.talk.a2a.governance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * O hook NATIVO do SDK para autorizar ANTES do aceite da task.
 *
 * <p>O {@code DefaultRequestHandler} chama {@code checkCreate} (task nova) ou {@code checkWrite}
 * (task existente) no inicio de {@code onMessageSend}, antes de construir o
 * {@code RequestContext}, antes de a task ir para {@code submitted} e antes de o
 * {@code AgentExecutor} ser agendado. Ou seja: a decisao acontece em t0, no lugar certo,
 * usando o mesmo {@link PolicyEngine} da borda.
 *
 * <p>Ele tambem fecha um buraco que a borda sozinha nao fecha: o token e amarrado a uma task
 * ({@code ScopeToken.taskId}). Se alguem apresentar um token valido de OUTRA task, a borda
 * aprova (o token e legitimo) e ESTA checagem recusa.
 *
 * <p>O SDK e fail-closed: negar aqui devolve {@code TaskNotFoundError}, de modo que o chamador
 * nao consegue distinguir "nao existe" de "nao autorizado".
 */
public class ScopeTokenTaskAuthorizationProvider implements TaskAuthorizationProvider {

    private static final Logger LOG =
            LoggerFactory.getLogger(ScopeTokenTaskAuthorizationProvider.class);

    private final PolicyEngine policyEngine;
    private final String skillId;
    private final Map<String, String> ownership = new ConcurrentHashMap<>();

    public ScopeTokenTaskAuthorizationProvider(PolicyEngine policyEngine, String skillId) {
        this.policyEngine = policyEngine;
        this.skillId = skillId;
    }

    @Override
    public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
        return decide(context, null, operation);
    }

    @Override
    public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
        return decide(context, taskId, operation);
    }

    @Override
    public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
        ScopeToken token = GovernedServerCallContextFactory.tokenOf(context);
        if (token == null) {
            return false;
        }
        if (TaskOperation.LIST_TASKS == operation && taskId.isEmpty()) {
            return true;
        }
        String owner = ownership.get(taskId);
        // Fail-closed: sem registro de dono, nao le.
        return owner != null && owner.equals(token.onBehalfOf());
    }

    @Override
    public boolean isTaskRecorded(String taskId) {
        return ownership.containsKey(taskId);
    }

    @Override
    public void recordOwnership(ServerCallContext context, String taskId, TaskOperation operation) {
        ScopeToken token = GovernedServerCallContextFactory.tokenOf(context);
        if (token != null) {
            // putIfAbsent: duas chamadas concorrentes para a mesma task nova nao se atropelam
            // (o SDK documenta essa corrida entre isTaskRecorded e recordOwnership).
            ownership.putIfAbsent(taskId, token.onBehalfOf());
        }
    }

    private boolean decide(ServerCallContext context, String taskId, TaskOperation operation) {
        String trace = GovernedServerCallContextFactory.traceOf(context);
        ScopeToken token = GovernedServerCallContextFactory.tokenOf(context);

        PolicyDecision decision = policyEngine.authorize(token, skillId,
                token == null ? null : token.maxAmount());

        if (!decision.allowed()) {
            LOG.warn("trace={} op={} task={} RECUSADA antes do aceite: {}",
                    trace, operation, taskId, decision.reason());
            return false;
        }

        // O vinculo entre token e operacao de negocio (ScopeToken.taskId x contextId da cadeia)
        // e conferido no executor, que e onde o contextId da mensagem esta disponivel:
        // aqui o SDK ainda nao construiu o RequestContext. Ver ChainCorrelation.

        LOG.info("trace={} op={} task={} autorizada antes do aceite por {} ({})",
                trace, operation, taskId == null ? token.taskId() : taskId,
                decision.policyId(), decision.reason());
        return true;
    }
}
