package br.com.talk.a2a.demob.credit;

import java.util.List;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.talk.a2a.governance.AgentRegistry;
import br.com.talk.a2a.governance.ChainCorrelation;
import br.com.talk.a2a.governance.GovernedLog;
import br.com.talk.a2a.governance.GovernedServerCallContextFactory;
import br.com.talk.a2a.governance.PolicyDecision;
import br.com.talk.a2a.governance.PolicyEngine;
import br.com.talk.a2a.governance.RefundRequest;
import br.com.talk.a2a.governance.ScopeToken;
import br.com.talk.a2a.governance.Scopes;
import br.com.talk.a2a.governance.TokenExchangeService;
import br.com.talk.a2a.spring.A2AOutboundClient;

/**
 * Executor do segundo salto.
 *
 * <p>Aqui aparece a checagem que a borda nao consegue fazer sozinha: o VALOR. A borda valida
 * escopo e audiencia sem ler o corpo; o valor do estorno so e conhecido depois de parsear a
 * mensagem. Entao o mesmo {@link PolicyEngine} e chamado de novo, agora com o valor real —
 * e ainda assim ANTES de qualquer chamada ao proximo agente.
 */
public class CreditExecutor implements AgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CreditExecutor.class);

    private static final String AGENT_NAME = "credit-agent";
    private static final String NEXT_AGENT = "payment-agent";

    private final CreditAnalystAgent analyst;
    private final AgentRegistry registry;
    private final TokenExchangeService tokenExchange;
    private final PolicyEngine policyEngine;

    public CreditExecutor(CreditAnalystAgent analyst,
                          AgentRegistry registry,
                          TokenExchangeService tokenExchange,
                          PolicyEngine policyEngine) {
        this.analyst = analyst;
        this.registry = registry;
        this.tokenExchange = tokenExchange;
        this.policyEngine = policyEngine;
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
        // A correlacao da operacao de negocio, propagada pelo salto anterior. Ver ChainCorrelation.
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

        RefundRequest request = RefundRequest.fromJson(context.getUserInput());

        // Terceira barreira, agora com o valor real em maos. Limites em codigo, item 8.
        PolicyDecision decision =
                policyEngine.authorize(token, Scopes.SKILL_REFUND_APPROVE, request.amount());
        if (!decision.allowed()) {
            LOG.warn("task={}  trace={}  agent={} APROVACAO NEGADA: {}",
                    taskId, trace, AGENT_NAME, decision.reason());
            emitter.reject(text(emitter, "Estorno nao aprovado: " + decision.reason()));
            return;
        }

        String assessment;
        try {
            assessment = analyst.assess("Cliente " + request.customerId()
                    + ", valor " + GovernedLog.brl(request.amount())
                    + ", motivo: " + request.reason());
        } catch (RuntimeException e) {
            LOG.warn("LLM indisponivel ({}), seguindo sem o parecer do modelo", e.getMessage());
            assessment = "Parecer automatico indisponivel; decisao tomada pela politica.";
        }

        LOG.info("task={}  trace={}  agent={} action=approve amount={} on_behalf_of={} "
                        + "scope={} authorized_by={}",
                taskId, trace, AGENT_NAME, GovernedLog.brl(request.amount()),
                token.onBehalfOf(), token.scope(), decision.policyId());

        // Checklist item 5: mais um degrau para baixo. E agora single_use: o token que vai
        // para quem move dinheiro so serve uma vez.
        ScopeToken delegated = tokenExchange.attenuate(
                token,
                NEXT_AGENT,
                Scopes.PAYMENT_EXECUTE_MAX_5000,
                Scopes.MAX_AMOUNT,
                true);

        AgentCard paymentCard = registry.resolve(NEXT_AGENT);

        try {
            Task downstream = A2AOutboundClient.sendText(
                    paymentCard,
                    RefundRequest.toJson(request),
                    taskId,                       // MESMA correlacao em toda a cadeia
                    delegated.raw());

            emitter.addArtifact(List.<Part<?>>of(new TextPart(
                    "Parecer: " + assessment + "\nPagamento: "
                            + A2AOutboundClient.artifactsText(downstream), null)));
            emitter.complete();

        } catch (Exception e) {
            LOG.error("task={}  trace={}  falha ao chamar {}: {}",
                    taskId, trace, NEXT_AGENT, e.getMessage());
            emitter.fail(text(emitter, "Falha no salto para " + NEXT_AGENT + ": " + e.getMessage()));
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) {
        emitter.cancel();
    }

    private static org.a2aproject.sdk.spec.Message text(AgentEmitter emitter, String value) {
        return emitter.newAgentMessage(List.<Part<?>>of(new TextPart(value, null)), null);
    }
}
