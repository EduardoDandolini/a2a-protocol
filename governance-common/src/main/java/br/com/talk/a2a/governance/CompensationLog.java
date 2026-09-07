package br.com.talk.a2a.governance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Registro de compensacao (saga).
 *
 * <p>Checklist item 7: "Compensacao para toda acao com efeito colateral."
 *
 * <p>Chamadas entre agentes nao sao transacionais. Nao existe rollback de um POST HTTP que
 * ja saiu. O que existe e a acao inversa — e ela precisa estar registrada ANTES ou JUNTO com
 * a acao original, com dados suficientes para ser executada por outra pessoa, as 3 da manha,
 * sem perguntar nada ao agente que a criou.
 *
 * <p>Interface de proposito: um {@code JpaCompensationLog} entra no lugar do
 * {@link InMemoryCompensationLog} sem tocar no agente. Numa implementacao real este registro
 * seria gravado na MESMA transacao do efeito colateral (outbox), nunca depois.
 */
public interface CompensationLog {

    /** Estado do registro de compensacao. */
    enum Status {
        /** Registrada: a acao inversa esta descrita e pronta para ser executada. */
        REGISTERED,
        /** Executada: a acao inversa foi aplicada. */
        COMPENSATED
    }

    /**
     * @param compensationId identificador do registro
     * @param taskId         a task A2A que originou a acao (mesma chave da idempotencia)
     * @param traceId        correlacao distribuida
     * @param agent          quem executou a acao
     * @param action         a acao executada, ex. {@code refund}
     * @param inverseAction  a acao inversa, ex. {@code reverse_refund}
     * @param subject        o alvo, ex. {@code customer:99182}
     * @param amount         o valor movimentado
     * @param externalRef    referencia externa do efeito colateral (id do lancamento)
     * @param onBehalfOf     em nome de quem a acao foi feita
     * @param authorizedBy   qual politica autorizou
     * @param status         estado atual
     * @param registeredAt   quando foi registrada
     */
    record CompensationEntry(String compensationId,
                             String taskId,
                             String traceId,
                             String agent,
                             String action,
                             String inverseAction,
                             String subject,
                             BigDecimal amount,
                             String externalRef,
                             String onBehalfOf,
                             String authorizedBy,
                             Status status,
                             Instant registeredAt) {
    }

    /** Registra a acao inversa. Devolve o registro criado. */
    CompensationEntry register(CompensationEntry entry);

    /** Marca uma compensacao como efetivamente executada. */
    void markCompensated(String compensationId);

    Optional<CompensationEntry> findByTaskId(String taskId);

    /** Append-only: tudo que foi registrado, na ordem. */
    List<CompensationEntry> all();
}
