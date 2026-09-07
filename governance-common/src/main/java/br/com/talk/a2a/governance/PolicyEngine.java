package br.com.talk.a2a.governance;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * O motor de politica: decide QUAL escopo cada skill exige e se um token dado satisfaz
 * essa exigencia.
 *
 * <p>Checklist item 4 ("autorizacao antes de aceitar a task") e item 8 ("limites em codigo").
 * Esta classe nao sabe nada sobre HTTP, sobre LLM, nem sobre A2A: e so a regra. Por isso ela
 * pode ser chamada na borda, no {@code TaskAuthorizationProvider} e de novo antes do efeito
 * colateral, sem duplicar logica.
 *
 * <p>"System prompt nao e ACL. Se sua alcada mora dentro de tres crases, voce nao tem alcada."
 */
public class PolicyEngine {

    /**
     * O que uma skill exige de quem a invoca.
     *
     * @param requiredScope escopo obrigatorio
     * @param maxAmount     teto de valor, ou {@code null} se a skill nao move dinheiro
     * @param singleUse     se o token precisa ser de uso unico
     */
    public record SkillRequirement(String requiredScope,
                                   @Nullable BigDecimal maxAmount,
                                   boolean singleUse) {
    }

    private final String policyId;
    private final Map<String, SkillRequirement> requirements;

    public PolicyEngine(String policyId, Map<String, SkillRequirement> requirements) {
        this.policyId = policyId;
        this.requirements = Map.copyOf(requirements);
    }

    /**
     * A politica da cadeia de estorno da Demo B. Os tres saltos, e o que cada um exige.
     */
    public static PolicyEngine refundChain() {
        Map<String, SkillRequirement> map = new LinkedHashMap<>();
        map.put(Scopes.SKILL_REFUND_REQUEST,
                new SkillRequirement(Scopes.REFUND_REQUEST, null, false));
        map.put(Scopes.SKILL_REFUND_APPROVE,
                new SkillRequirement(Scopes.REFUND_APPROVE_MAX_5000, Scopes.MAX_AMOUNT, false));
        map.put(Scopes.SKILL_PAYMENT_EXECUTE,
                new SkillRequirement(Scopes.PAYMENT_EXECUTE_MAX_5000, Scopes.MAX_AMOUNT, true));
        return new PolicyEngine(Scopes.POLICY_ENGINE_ID, map);
    }

    public String policyId() {
        return policyId;
    }

    public SkillRequirement requirementFor(String skillId) {
        SkillRequirement requirement = requirements.get(skillId);
        if (requirement == null) {
            throw AuthorizationDeniedException.forbidden("Skill desconhecida: " + skillId);
        }
        return requirement;
    }

    /**
     * A decisao. Chamada ANTES de a task ser aceita, e de novo antes do efeito colateral.
     *
     * @param token   token delegado ja verificado (pode ser {@code null}: sem token nao ha decisao)
     * @param skillId skill que se pretende invocar
     * @param amount  valor da operacao, ou {@code null} quando nao ha valor envolvido
     */
    public PolicyDecision authorize(@Nullable ScopeToken token,
                                    String skillId,
                                    @Nullable BigDecimal amount) {
        if (token == null) {
            return PolicyDecision.deny(policyId,
                    "Nenhum token apresentado para a skill " + skillId);
        }

        SkillRequirement requirement = requirementFor(skillId);

        if (!requirement.requiredScope().equals(token.scope())) {
            return PolicyDecision.deny(policyId,
                    "Escopo insuficiente para " + skillId + ": token traz '" + token.scope()
                            + "', a skill exige '" + requirement.requiredScope() + "'");
        }

        if (requirement.singleUse() && !token.singleUse()) {
            return PolicyDecision.deny(policyId,
                    "A skill " + skillId + " exige token de uso unico (single_use)");
        }

        if (requirement.maxAmount() != null) {
            if (amount == null) {
                return PolicyDecision.deny(policyId,
                        "A skill " + skillId + " movimenta valores e nenhum valor foi informado");
            }
            if (amount.compareTo(requirement.maxAmount()) > 0) {
                return PolicyDecision.deny(policyId,
                        "Valor " + amount + " acima da alcada da politica ("
                                + requirement.maxAmount() + ") para " + skillId);
            }
            if (token.maxAmount() == null || amount.compareTo(token.maxAmount()) > 0) {
                return PolicyDecision.deny(policyId,
                        "Valor " + amount + " acima do teto do token ("
                                + token.maxAmount() + ")");
            }
        }

        return PolicyDecision.allow(policyId,
                "Escopo " + token.scope() + " satisfaz " + skillId);
    }
}
