package br.com.talk.a2a.demob.customer;

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
import br.com.talk.a2a.governance.GovernedServerCallContextFactory;
import br.com.talk.a2a.governance.RefundRequest;
import br.com.talk.a2a.governance.ScopeToken;
import br.com.talk.a2a.governance.Scopes;
import br.com.talk.a2a.governance.TokenExchangeService;
import br.com.talk.a2a.spring.A2AOutboundClient;

/**
 * Executor do primeiro salto.
 *
 * <p>Quando este metodo comeca a rodar, a autorizacao JA aconteceu duas vezes: no
 * {@code AuthorizationInterceptor} (borda HTTP) e no
 * {@code ScopeTokenTaskAuthorizationProvider} (hook do SDK, antes do aceite). A task so
 * existe porque um token com escopo {@code refund:request} amarrado a ESTA task foi
 * apresentado.
 *
 * <p>O trabalho aqui e outro: atenuar o token e chamar o proximo agente.
 */
public class CustomerServiceExecutor implements AgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(CustomerServiceExecutor.class);

    private static final String AGENT_NAME = "customer-service-agent";
    private static final String NEXT_AGENT = "credit-agent";

    private final TriageAgent triageAgent;
    private final AgentRegistry registry;
    private final TokenExchangeService tokenExchange;

    public CustomerServiceExecutor(TriageAgent triageAgent,
                                   AgentRegistry registry,
                                   TokenExchangeService tokenExchange) {
        this.triageAgent = triageAgent;
        this.registry = registry;
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

        if (token == null) {
            // Nao deveria acontecer: a borda e o provider ja teriam recusado. Fail-closed
            // mesmo assim — a terceira barreira nao confia nas duas anteriores.
            emitter.fail(emitter.newAgentMessage(
                    List.<Part<?>>of(new TextPart("Sem token delegado no contexto.", null)), null));
            return;
        }

        RefundRequest request = RefundRequest.fromJson(context.getUserInput());

        // O identificador estavel da operacao de negocio, o mesmo nos tres saltos.
        String taskId = ChainCorrelation.of(context);

        // O token e amarrado a ESTA operacao. Um token legitimo de outra operacao para aqui.
        if (!taskId.equals(token.taskId())) {
            LOG.warn("task={}  trace={}  token emitido para {} apresentado em {}: recusado",
                    taskId, trace, token.taskId(), taskId);
            emitter.reject(emitter.newAgentMessage(List.<Part<?>>of(
                    new TextPart("Token nao corresponde a esta operacao.", null)), null));
            return;
        }

        // O modelo opina. Nada do que ele responder muda o escopo, o teto ou o destino.
        String triage;
        try {
            triage = triageAgent.triage(request.reason());
        } catch (RuntimeException e) {
            LOG.warn("LLM indisponivel ({}), seguindo sem a triagem do modelo", e.getMessage());
            triage = "Triagem automatica indisponivel; seguindo pelo fluxo governado.";
        }

        LOG.info("task={}  trace={}  agent={} triagem=\"{}\"", taskId, trace, AGENT_NAME, triage);

        // Checklist item 5: escopo atenuado a cada salto. O token que recebemos NAO vai adiante.
        ScopeToken delegated = tokenExchange.attenuate(
                token,
                NEXT_AGENT,
                Scopes.REFUND_APPROVE_MAX_5000,
                Scopes.MAX_AMOUNT,
                false);

        // Checklist item 1: o destino vem do registry curado, com card assinado e verificado.
        AgentCard creditCard = registry.resolve(NEXT_AGENT);

        try {
            Task downstream = A2AOutboundClient.sendText(
                    creditCard,
                    RefundRequest.toJson(request),
                    taskId,                       // MESMA correlacao em toda a cadeia
                    delegated.raw());

            String answer = A2AOutboundClient.artifactsText(downstream);
            emitter.addArtifact(List.<Part<?>>of(new TextPart(
                    "Triagem: " + triage + "\nCredito/pagamento: " + answer, null)));
            emitter.complete();

        } catch (Exception e) {
            LOG.error("task={}  trace={}  falha ao chamar {}: {}",
                    taskId, trace, NEXT_AGENT, e.getMessage());
            emitter.fail(emitter.newAgentMessage(
                    List.<Part<?>>of(new TextPart("Falha no salto para " + NEXT_AGENT + ": "
                            + e.getMessage(), null)), null));
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) {
        emitter.cancel();
    }
}
