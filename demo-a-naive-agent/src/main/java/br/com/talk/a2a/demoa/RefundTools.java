package br.com.talk.a2a.demoa;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * As tools do agente ingenuo.
 *
 * <p>Repare no que NAO existe aqui: nao ha token, nao ha escopo, nao ha alcada, nao ha
 * chave de idempotencia, nao ha registro de compensacao. A unica "autorizacao" e o LLM
 * ter decidido chamar a tool.
 *
 * <p>Este e o momento t3 da palestra. Se a sua alcada mora dentro de tres crases no system
 * prompt, voce nao tem alcada — o modelo pode ser convencido a ignora-la, e nada abaixo dele
 * vai discordar.
 */
@Component
public class RefundTools {

    private static final Logger LOG = LoggerFactory.getLogger(RefundTools.class);
    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    private final RefundLedger ledger;

    public RefundTools(RefundLedger ledger) {
        this.ledger = ledger;
    }

    @Tool("Executa o estorno de um valor para um cliente. Use quando a solicitacao for procedente.")
    public String issueRefund(@P("identificador do cliente") String customerId,
                              @P("valor do estorno em reais") double amount) {
        BigDecimal value = BigDecimal.valueOf(amount);
        RefundLedger.RefundRecord record = ledger.execute(customerId, value);

        // O log do incidente. Ele diz o QUE aconteceu e nao diz NADA sobre quem autorizou:
        // sem task id, sem trace, sem on_behalf_of, sem escopo, sem policy engine.
        LOG.info("action=refund customer={} amount={} refund_id={} reason=\"o agente determinou"
                        + " que a solicitacao era apropriada\"",
                customerId, BRL.format(value), record.refundId());

        return "Estorno de " + BRL.format(value) + " executado para o cliente " + customerId
                + " (id " + record.refundId() + ").";
    }

    @Tool("Consulta o total ja estornado para conferencia.")
    public String totalRefunded() {
        return "Total estornado ate agora: " + BRL.format(ledger.totalRefunded());
    }
}
