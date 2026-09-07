package br.com.talk.a2a.demob.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.talk.a2a.governance.AuthorizationDeniedException;
import br.com.talk.a2a.governance.ChainCorrelation;
import br.com.talk.a2a.governance.CompensationLog;
import br.com.talk.a2a.governance.GovernedLog;
import br.com.talk.a2a.governance.GovernedServerCallContextFactory;
import br.com.talk.a2a.governance.IdempotencyStore;
import br.com.talk.a2a.governance.PolicyDecision;
import br.com.talk.a2a.governance.PolicyEngine;
import br.com.talk.a2a.governance.RefundRequest;
import br.com.talk.a2a.governance.ScopeToken;
import br.com.talk.a2a.governance.Scopes;
import br.com.talk.a2a.governance.TokenExchangeService;

/**
 * O executor que move dinheiro. Leia-o lado a lado com o
 * {@code NaiveRefundAgentExecutor} da Demo A.
 *
 * <p>A ordem das operacoes e o conteudo da palestra:
 * <ol>
 *   <li>o token ja foi verificado na borda e no hook de aceite do SDK — a task so existe
 *       porque alguem provou ter {@code payment:execute:max_5000} para ESTA task;</li>
 *   <li>o token de uso unico e consumido (replay do mesmo token morre aqui);</li>
 *   <li>a idempotencia por task ID reserva a execucao (duplicata de negocio morre aqui);</li>
 *   <li>a politica e avaliada de novo, agora com o valor real;</li>
 *   <li>o efeito colateral acontece;</li>
 *   <li>a compensacao e registrada;</li>
 *   <li>o log responde quem autorizou.</li>
 * </ol>
 */
public class PaymentExecutor implements AgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentExecutor.class);

    private static final String AGENT_NAME = "payment-agent";
    private static final String ACTION = "refund";
    private static final String INVERSE_ACTION = "reverse_refund";

    private final PaymentLedger ledger;
    private final PolicyEngine policyEngine;
    private final IdempotencyStore idempotencyStore;
    private final CompensationLog compensationLog;
    private final TokenExchangeService tokenExchange;

    public PaymentExecutor(PaymentLedger ledger,
                           PolicyEngine policyEngine,
                           IdempotencyStore idempotencyStore,
                           CompensationLog compensationLog,
                           TokenExchangeService tokenExchange) {
        this.ledger = ledger;
        this.policyEngine = policyEngine;
        this.idempotencyStore = idempotencyStore;
        this.compensationLog = compensationLog;
        this.tokenExchange = tokenExchange;
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) {
        if (context.getTask() == null) {
            emitter.submit();
        }
        emitter.startWork();

        ServerCallContext callContext = context.getCallContext();
        ScopeToken token = callContext == null
                ? null : GovernedServerCallContextFactory.tokenOf(callContext);
        String trace = callContext == null
                ? "-" : GovernedServerCallContextFactory.traceOf(callContext);
        // A chave de idempotencia: a correlacao da operacao de negocio, estavel nos tres
        // saltos. Ver ChainCorrelation para por que nao e context.getTaskId().
        String taskId = ChainCorrelation.of(context);

        if (token == null) {
            emitter.fail(text(emitter, "Sem token delegado no contexto."));
            return;
        }

        if (!taskId.equals(token.taskId())) {
            LOG.warn("task={}  trace={}  token emitido para {}: recusado", taskId, trace, token.taskId());
            emitter.reject(text(emitter, "Token nao corresponde a esta operacao."));
            return;
        }

        RefundRequest request;
        try {
            request = RefundRequest.fromJson(context.getUserInput());
        } catch (IllegalArgumentException e) {
            emitter.fail(text(emitter, e.getMessage()));
            return;
        }

        // ---------------------------------------------------------------------
        // Checklist item 6: IDEMPOTENCIA POR TASK ID.
        // A chave vem do protocolo e atravessa os tres saltos — nao e inventada aqui.
        // ---------------------------------------------------------------------
        Optional<IdempotencyStore.Execution> existing = idempotencyStore.reserve(taskId);
        if (existing.isPresent()) {
            IdempotencyStore.Execution original = existing.get();
            GovernedLog.duplicateSuppressed(LOG, taskId, original.executedAt());
            emitter.addArtifact(List.<Part<?>>of(new TextPart(
                    "Duplicata suprimida por idempotencia (task " + taskId + "). "
                            + "Execucao original em " + original.executedAt() + ": "
                            + original.resultSummary(), null)));
            emitter.complete();
            return;
        }

        try {
            // Token de uso unico: um replay do MESMO token nao passa daqui.
            tokenExchange.consume(token);

            // Ultima avaliacao de politica, com o valor real. Item 8: limites em codigo.
            PolicyDecision decision =
                    policyEngine.authorize(token, Scopes.SKILL_PAYMENT_EXECUTE, request.amount());
            decision.orThrow();

            // Efeito colateral.
            PaymentLedger.Entry entry = ledger.credit(request.customerId(), request.amount());

            // ---------------------------------------------------------------------
            // Checklist item 7: COMPENSACAO para toda acao com efeito colateral.
            // Registrada junto com a acao, com dados suficientes para alguem executar a
            // acao inversa as 3 da manha sem perguntar nada a este agente.
            // ---------------------------------------------------------------------
            CompensationLog.CompensationEntry compensation = compensationLog.register(
                    new CompensationLog.CompensationEntry(
                            "cmp-" + UUID.randomUUID().toString().substring(0, 8),
                            taskId,
                            trace,
                            AGENT_NAME,
                            ACTION,
                            INVERSE_ACTION,
                            request.principal(),
                            request.amount(),
                            entry.externalRef(),
                            token.onBehalfOf(),
                            decision.policyId(),
                            CompensationLog.Status.REGISTERED,
                            Instant.now()));

            String summary = "estorno " + GovernedLog.brl(request.amount())
                    + " lancado como " + entry.externalRef();
            idempotencyStore.complete(taskId, summary);

            // ---------------------------------------------------------------------
            // A linha de log que responde a pergunta da palestra.
            // ---------------------------------------------------------------------
            GovernedLog.action(LOG, taskId, trace, AGENT_NAME, ACTION, request.amount(),
                    token, decision.policyId(), "registered");

            emitter.addArtifact(List.<Part<?>>of(new TextPart(
                    "Estorno de " + GovernedLog.brl(request.amount())
                            + " executado para " + request.principal()
                            + " (ref " + entry.externalRef() + ", compensacao "
                            + compensation.compensationId() + ").", null)));
            emitter.complete();

        } catch (AuthorizationDeniedException denied) {
            // Libera a reserva: nao houve efeito colateral, entao um retry legitimo deve poder
            // tentar de novo.
            idempotencyStore.release(taskId);
            LOG.warn("task={}  trace={}  agent={} PAGAMENTO NEGADO: {}",
                    taskId, trace, AGENT_NAME, denied.getMessage());
            emitter.reject(text(emitter, "Pagamento negado: " + denied.getMessage()));

        } catch (RuntimeException e) {
            idempotencyStore.release(taskId);
            LOG.error("task={}  trace={}  agent={} falha na execucao: {}",
                    taskId, trace, AGENT_NAME, e.getMessage(), e);
            emitter.fail(text(emitter, "Falha ao executar o pagamento: " + e.getMessage()));
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) {
        emitter.cancel();
    }

    private static Message text(AgentEmitter emitter, String value) {
        return emitter.newAgentMessage(List.<Part<?>>of(new TextPart(value, null)), null);
    }
}
