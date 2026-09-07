package br.com.talk.a2a.governance;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Idempotencia em memoria.
 *
 * <p>Simplificacao de demo: um {@link ConcurrentHashMap}. A reserva usa
 * {@code putIfAbsent}, entao duas chamadas concorrentes com o mesmo task ID nao conseguem
 * as duas executar — a segunda enxerga a reserva da primeira. Numa implementacao JPA a
 * mesma garantia viria de uma unique constraint no {@code task_id}.
 */
public class InMemoryIdempotencyStore implements IdempotencyStore {

    /** Marcador de "reservado, ainda executando". */
    private static final String IN_PROGRESS = "__in_progress__";

    private final ConcurrentHashMap<String, Execution> executions = new ConcurrentHashMap<>();

    @Override
    public Optional<Execution> reserve(String taskId) {
        Execution reservation = new Execution(taskId, IN_PROGRESS, Instant.now());
        Execution existing = executions.putIfAbsent(taskId, reservation);
        return Optional.ofNullable(existing);
    }

    @Override
    public void complete(String taskId, String resultSummary) {
        executions.computeIfPresent(taskId,
                (key, current) -> new Execution(key, resultSummary, current.executedAt()));
    }

    @Override
    public void release(String taskId) {
        executions.computeIfPresent(taskId,
                (key, current) -> IN_PROGRESS.equals(current.resultSummary()) ? null : current);
    }

    @Override
    public Optional<Execution> find(String taskId) {
        return Optional.ofNullable(executions.get(taskId));
    }
}
