package br.com.talk.a2a.demoa;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * "Razao" de estornos em memoria. Sem idempotencia, de proposito.
 *
 * <p>Cada chamada de {@link #execute} debita de novo. E este o bug da Demo A: mande a MESMA
 * task duas vezes e o cliente recebe R$ 4.812,00 duas vezes.
 */
@Component
public class RefundLedger {

    /** Registro de um estorno efetivamente executado. */
    public record RefundRecord(String refundId, String customerId, BigDecimal amount, long timestampMillis) {
    }

    private final List<RefundRecord> records = new CopyOnWriteArrayList<>();

    public RefundRecord execute(String customerId, BigDecimal amount) {
        // Nenhuma chave de idempotencia. Nenhuma checagem de limite. Nenhum registro de
        // compensacao. Nenhuma pergunta sobre quem autorizou.
        RefundRecord record = new RefundRecord(
                "rfnd-" + Long.toHexString(System.nanoTime()),
                customerId,
                amount,
                System.currentTimeMillis());
        records.add(record);
        return record;
    }

    public List<RefundRecord> all() {
        return List.copyOf(records);
    }

    public BigDecimal totalRefunded() {
        return records.stream().map(RefundRecord::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
