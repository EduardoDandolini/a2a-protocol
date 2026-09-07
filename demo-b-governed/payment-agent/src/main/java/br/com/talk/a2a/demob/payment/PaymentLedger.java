package br.com.talk.a2a.demob.payment;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * O efeito colateral real: o lancamento do estorno.
 *
 * <p>Deliberadamente igual ao {@code RefundLedger} da Demo A — sem idempotencia propria.
 * A garantia nao vem daqui; vem do {@code IdempotencyStore} chamado antes. E esse e o ponto:
 * a idempotencia precisa estar na fronteira da operacao de negocio, nao escondida no
 * repositorio.
 */
@Component
public class PaymentLedger {

    public record Entry(String externalRef, String customerId, BigDecimal amount, long timestampMillis) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    public Entry credit(String customerId, BigDecimal amount) {
        Entry entry = new Entry(
                "pmt-" + Long.toHexString(System.nanoTime()),
                customerId,
                amount,
                System.currentTimeMillis());
        entries.add(entry);
        return entry;
    }

    public List<Entry> all() {
        return List.copyOf(entries);
    }

    public BigDecimal total() {
        return entries.stream().map(Entry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
