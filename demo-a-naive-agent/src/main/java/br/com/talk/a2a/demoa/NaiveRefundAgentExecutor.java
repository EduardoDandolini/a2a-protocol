package br.com.talk.a2a.demoa;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * O {@link AgentExecutor} da Demo A.
 *
 * <p>Compare com o da Demo B ({@code GovernedPaymentAgentExecutor}) linha a linha. Aqui:
 * <ul>
 *   <li>o {@code ServerCallContext} existe e nunca e consultado — ninguem pergunta quem chamou;</li>
 *   <li>{@code context.getTaskId()} existe e nunca e usado como chave de idempotencia;</li>
 *   <li>nao ha registro de compensacao;</li>
 *   <li>a unica "alcada" e a frase em portugues dentro do system message do
 *       {@link RefundAgent}.</li>
 * </ul>
 *
 * <p>Mande a mesma task duas vezes e o estorno sai duas vezes. E o log so vai dizer que
 * "o agente determinou que a solicitacao era apropriada".
 */
public class NaiveRefundAgentExecutor implements AgentExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(NaiveRefundAgentExecutor.class);

    /** Valor em reais no formato 4.812,00 / 4812.00 / 4812 */
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:R\\$\\s*)?(\\d{1,3}(?:\\.\\d{3})*,\\d{2}|\\d+(?:[.,]\\d{1,2})?)");
    private static final Pattern CUSTOMER = Pattern.compile("(?:cliente|customer)[:\\s]+(\\w+)",
            Pattern.CASE_INSENSITIVE);

    private final RefundAgent refundAgent;
    private final RefundLedger ledger;

    public NaiveRefundAgentExecutor(RefundAgent refundAgent, RefundLedger ledger) {
        this.refundAgent = refundAgent;
        this.ledger = ledger;
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) {
        // t0: a task e aceita. Nenhuma pergunta e feita sobre o chamador.
        if (context.getTask() == null) {
            emitter.submit();
        }
        emitter.startWork();

        String input = context.getUserInput();
        LOG.info("task={} recebida, executando o agente", context.getTaskId());

        String answer;
        try {
            // t1..t3: o modelo decide sozinho se e quanto estornar, e chama a tool.
            answer = refundAgent.handle(input);
        } catch (RuntimeException e) {
            // Se o Ollama nao estiver disponivel a demo ainda precisa reproduzir o incidente,
            // entao caimos num caminho deterministico com o MESMO comportamento (sem
            // idempotencia, sem alcada real).
            LOG.warn("LLM indisponivel ({}), seguindo pelo caminho deterministico da demo",
                    e.getMessage());
            answer = fallback(input);
        }

        emitter.addArtifact(List.<Part<?>>of(new TextPart(answer, null)));
        emitter.complete();
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) {
        emitter.cancel();
    }

    private String fallback(String input) {
        Matcher customerMatcher = CUSTOMER.matcher(input);
        String customerId = customerMatcher.find() ? customerMatcher.group(1) : "desconhecido";

        Matcher amountMatcher = AMOUNT.matcher(input);
        BigDecimal amount = amountMatcher.find()
                ? new BigDecimal(amountMatcher.group(1).replace(".", "").replace(',', '.'))
                : BigDecimal.ZERO;

        RefundLedger.RefundRecord record = ledger.execute(customerId, amount);
        LOG.info("action=refund customer={} amount=R$ {} refund_id={} reason=\"o agente determinou"
                        + " que a solicitacao era apropriada\"",
                customerId, amount, record.refundId());
        return "Estorno de R$ " + amount + " executado para o cliente " + customerId
                + " (id " + record.refundId() + ").";
    }
}
