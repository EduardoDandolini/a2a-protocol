package br.com.talk.a2a.governance;

import java.time.Instant;
import java.util.Optional;

/**
 * Idempotencia por task ID.
 *
 * <p>Checklist item 6. A chave nao e inventada: e o proprio ID da task A2A, que ja existe
 * no protocolo e ja atravessa todos os saltos da cadeia. E por isso que o
 * {@code TokenExchangeService} amarra cada token a uma task — o retry, o replay e a
 * chamada duplicada carregam o mesmo ID, e param aqui.
 *
 * <p>Interface (e nao classe) de proposito: um {@code JpaIdempotencyStore} entra no lugar do
 * {@link InMemoryIdempotencyStore} sem tocar no agente.
 */
public interface IdempotencyStore {

    /** O que ficou registrado da primeira (e unica) execucao de uma task. */
    record Execution(String taskId, String resultSummary, Instant executedAt) {
    }

    /**
     * Tenta reservar a execucao de {@code taskId}.
     *
     * @return {@link Optional#empty()} se a reserva foi feita agora e o chamador DEVE executar;
     *         a execucao original se a task ja tinha sido executada (duplicata suprimida).
     */
    Optional<Execution> reserve(String taskId);

    /** Completa uma reserva com o resultado da execucao. */
    void complete(String taskId, String resultSummary);

    /** Libera a reserva quando a execucao falhou, para permitir um retry legitimo. */
    void release(String taskId);

    Optional<Execution> find(String taskId);
}
