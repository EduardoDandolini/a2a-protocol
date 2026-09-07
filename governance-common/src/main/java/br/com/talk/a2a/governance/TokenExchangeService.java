package br.com.talk.a2a.governance;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/**
 * Emissao e ATENUACAO de tokens delegados.
 *
 * <p>Checklist item 5: "Token delegado com escopo atenuado a cada salto."
 *
 * <p>A regra que esta interface existe para tornar obrigatoria: um agente NUNCA repassa o
 * proprio token adiante. Antes de chamar o proximo agente ele apresenta o token que recebeu
 * e pede um token novo, mais estreito, amarrado a mesma task, valido por poucos segundos.
 * Se o proximo agente for comprometido, o que vaza e um token que so serve para uma operacao
 * e um valor.
 *
 * <p>A implementacao de demo ({@link HmacTokenExchangeService}) assina com HMAC e guarda o
 * estado em memoria. Trocar por um IdP real (OAuth 2.0 Token Exchange, RFC 8693) deve ser
 * uma mudanca de configuracao, nao de arquitetura — por isso o contrato mora aqui.
 */
public interface TokenExchangeService {

    /**
     * Emite o token de borda (o primeiro da cadeia), representando o principal humano.
     */
    ScopeToken issue(String subject,
                     String audience,
                     String onBehalfOf,
                     String scope,
                     @Nullable BigDecimal maxAmount,
                     boolean singleUse,
                     String taskId);

    /**
     * Troca um token por outro MAIS ESTREITO para o proximo salto.
     *
     * @param current      o token que este agente recebeu e ja verificou
     * @param audience     o agente de destino
     * @param scope        o escopo do proximo salto (mais restrito que o atual)
     * @param maxAmount    o teto do proximo salto (nunca maior que o atual)
     * @param singleUse    se o token de destino e de uso unico
     * @throws AuthorizationDeniedException se a troca tentar ampliar privilegios
     */
    ScopeToken attenuate(ScopeToken current,
                         String audience,
                         String scope,
                         @Nullable BigDecimal maxAmount,
                         boolean singleUse);

    /**
     * Verifica assinatura, validade e vinculo com a task, devolvendo o token ja parseado.
     *
     * @throws AuthorizationDeniedException se o token for invalido, expirado, de outra task,
     *                                      ou single-use ja consumido
     */
    ScopeToken verify(String rawToken, String expectedAudience);

    /**
     * Marca um token single-use como consumido. Idempotente por {@code jti}.
     *
     * @throws AuthorizationDeniedException se o token ja tiver sido consumido
     */
    void consume(ScopeToken token);
}
