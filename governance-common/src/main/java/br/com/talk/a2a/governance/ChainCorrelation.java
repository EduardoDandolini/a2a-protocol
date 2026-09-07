package br.com.talk.a2a.governance;

import org.a2aproject.sdk.server.agentexecution.RequestContext;

/**
 * O identificador que atravessa TODA a cadeia de agentes — a chave de idempotencia.
 *
 * <p><b>Detalhe do protocolo que vale a pena saber:</b> em A2A 1.0 o ID da task e gerado pelo
 * SERVIDOR. Um cliente nao escolhe o task ID; se mandar um que nao existe, o agente responde
 * {@code TaskNotFound}. Ou seja: cada salto da cadeia tem seu proprio task ID, e "idempotencia
 * por task ID" nao pode significar "o task ID local deste agente" — senao cada salto teria uma
 * chave diferente e a duplicata passaria.
 *
 * <p>O identificador que o cliente PODE fixar e que o protocolo propaga entre tasks
 * relacionadas e o {@code contextId}. Entao e ele que carrega a identidade da operacao de
 * negocio ao longo dos tres saltos, e e ele que ancora:
 * <ul>
 *   <li>a idempotencia do payment-agent (checklist item 6);</li>
 *   <li>o vinculo do token delegado ({@code ScopeToken.taskId});</li>
 *   <li>a chave do registro de compensacao (checklist item 7);</li>
 *   <li>o campo {@code task=} da linha de log governada.</li>
 * </ul>
 *
 * <p>A licao continua sendo a da palestra — a chave de idempotencia precisa vir do protocolo,
 * nao ser inventada pelo agente. Aqui ela vem; so nao e o campo que o nome sugere a primeira
 * vista.
 */
public final class ChainCorrelation {

    private ChainCorrelation() {
    }

    /**
     * O ID da operacao de negocio, estavel em todos os saltos.
     *
     * @return o {@code contextId}, ou o task ID local caso ele falte
     */
    public static String of(RequestContext context) {
        String contextId = context.getContextId();
        return contextId == null || contextId.isBlank() ? context.getTaskId() : contextId;
    }
}
