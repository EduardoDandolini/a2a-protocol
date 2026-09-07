package br.com.talk.a2a.governance;

/**
 * O resultado de uma decisao de politica, com o motivo escrito.
 *
 * <p>O campo {@link #policyId()} e o que aparece como {@code authorized_by=} no log governado.
 * Nunca mais "o agente determinou que a solicitacao era apropriada".
 */
public record PolicyDecision(boolean allowed, String policyId, String reason) {

    public static PolicyDecision allow(String policyId, String reason) {
        return new PolicyDecision(true, policyId, reason);
    }

    public static PolicyDecision deny(String policyId, String reason) {
        return new PolicyDecision(false, policyId, reason);
    }

    /** Converte uma negacao em excecao 403; no-op quando permitido. */
    public PolicyDecision orThrow() {
        if (!allowed) {
            throw AuthorizationDeniedException.forbidden(reason);
        }
        return this;
    }
}
