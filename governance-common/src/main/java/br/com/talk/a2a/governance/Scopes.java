package br.com.talk.a2a.governance;

import java.math.BigDecimal;

/**
 * Os escopos da cadeia de estorno, e os IDs de skill a que eles correspondem.
 *
 * <p>Checklist item 8: "Limites em codigo. Nunca no prompt." A alcada de R$ 5.000,00 aparece
 * aqui, como {@link java.math.BigDecimal}, e nao dentro de tres crases de um system message.
 */
public final class Scopes {

    /** customer-service-agent -> credit-agent */
    public static final String REFUND_REQUEST = "refund:request";

    /** credit-agent -> payment-agent */
    public static final String REFUND_APPROVE_MAX_5000 = "refund:approve:max_5000";

    /** payment-agent (o salto que move dinheiro) */
    public static final String PAYMENT_EXECUTE_MAX_5000 = "payment:execute:max_5000";

    /** Escopo do token de borda que o cliente/canal apresenta ao customer-service-agent. */
    public static final String CUSTOMER_SUPPORT = "customer:support";

    /** IDs das skills declaradas nos Agent Cards da Demo B. */
    public static final String SKILL_REFUND_REQUEST = "refund.request";
    public static final String SKILL_REFUND_APPROVE = "refund.approve";
    public static final String SKILL_PAYMENT_EXECUTE = "payment.execute";

    /** A alcada, em codigo. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("5000.00");

    /** Identificacao da politica que aparece no campo {@code authorized_by} do log governado. */
    public static final String POLICY_ENGINE_ID = "policy-engine/v4";

    private Scopes() {
    }
}
