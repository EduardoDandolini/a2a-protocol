package br.com.talk.a2a.governance;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;

/**
 * A linha de log que responde "quem autorizou esse agente?".
 *
 * <p>Compare com a linha da Demo A:
 * <pre>
 * action=refund customer=99182 amount=R$ 4.812,00 reason="o agente determinou que a solicitacao era apropriada"
 * </pre>
 * Ela diz o que aconteceu e nao responde nada. A linha governada responde tudo:
 * <pre>
 * INFO  task=a7f3c9  trace=8b1e...  agent=payment-agent action=refund amount=R$ 4.812,00 delegated_from=credit-agent on_behalf_of=customer:99182 scope=payment:execute:max_5000 authorized_by=policy-engine/v4 idempotency_key=a7f3c9 compensation=registered
 * WARN  task=a7f3c9  duplicate suppressed  original=2026-09-07T12:00:00Z
 * </pre>
 *
 * <p>Cada campo aqui existe porque alguem, num incidente, vai precisar dele:
 * {@code delegated_from} para reconstruir a cadeia, {@code on_behalf_of} para achar o cliente,
 * {@code scope} para saber o que era permitido, {@code authorized_by} para saber qual regra
 * decidiu, {@code idempotency_key} para saber por que nao houve duplicata, e
 * {@code compensation} para saber que existe uma acao inversa registrada.
 */
public final class GovernedLog {

    private static final NumberFormat BRL = NumberFormat.getCurrencyInstance(Locale.of("pt", "BR"));

    private GovernedLog() {
    }

    /** Formata um valor como {@code R$ 4.812,00} (espaco normal, nao no-break). */
    public static String brl(BigDecimal amount) {
        return BRL.format(amount).replace(' ', ' ');
    }

    /** A linha INFO da acao autorizada e executada. */
    public static void action(Logger log,
                              String taskId,
                              String traceId,
                              String agent,
                              String action,
                              BigDecimal amount,
                              ScopeToken token,
                              String authorizedBy,
                              String compensationStatus) {
        log.info("task={}  trace={}  agent={} action={} amount={} delegated_from={} "
                        + "on_behalf_of={} scope={} authorized_by={} idempotency_key={} compensation={}",
                taskId,
                traceId,
                agent,
                action,
                brl(amount),
                token.delegatedFrom() == null ? "-" : token.delegatedFrom(),
                token.onBehalfOf(),
                token.scope(),
                authorizedBy,
                taskId,
                compensationStatus);
    }

    /** A linha WARN do climax da Demo B: a segunda vez que a MESMA task chega. */
    public static void duplicateSuppressed(Logger log, String taskId, Instant originalExecutedAt) {
        log.warn("task={}  duplicate suppressed  original={}", taskId, originalExecutedAt);
    }
}
