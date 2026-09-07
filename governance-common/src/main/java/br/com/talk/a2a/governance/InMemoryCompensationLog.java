package br.com.talk.a2a.governance;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Log de compensacao append-only em memoria.
 *
 * <p>Simplificacao de demo. Em producao isto e uma tabela — na mesma transacao do efeito
 * colateral — e o {@code markCompensated} vira um UPDATE, mantendo o registro original
 * intacto para auditoria. A palestra menciona "task store em JPA": vale igual aqui.
 */
public class InMemoryCompensationLog implements CompensationLog {

    private final List<CompensationEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public CompensationEntry register(CompensationEntry entry) {
        entries.add(entry);
        return entry;
    }

    @Override
    public void markCompensated(String compensationId) {
        for (int i = 0; i < entries.size(); i++) {
            CompensationEntry entry = entries.get(i);
            if (entry.compensationId().equals(compensationId)) {
                entries.set(i, new CompensationEntry(entry.compensationId(), entry.taskId(),
                        entry.traceId(), entry.agent(), entry.action(), entry.inverseAction(),
                        entry.subject(), entry.amount(), entry.externalRef(), entry.onBehalfOf(),
                        entry.authorizedBy(), Status.COMPENSATED, entry.registeredAt()));
                return;
            }
        }
    }

    @Override
    public Optional<CompensationEntry> findByTaskId(String taskId) {
        return entries.stream().filter(entry -> entry.taskId().equals(taskId)).findFirst();
    }

    @Override
    public List<CompensationEntry> all() {
        return List.copyOf(entries);
    }
}
