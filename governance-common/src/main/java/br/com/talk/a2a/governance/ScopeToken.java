package br.com.talk.a2a.governance;

import java.math.BigDecimal;
import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * Um token delegado, ja verificado, com escopo atenuado.
 *
 * <p>E o "cracha" de verdade da Demo B. Diferente do Agent Card, ele:
 * <ul>
 *   <li>e assinado por um emissor conhecido e verificado a cada salto;</li>
 *   <li>carrega UM escopo, o mais estreito possivel para o proximo salto;</li>
 *   <li>carrega o limite de valor como numero, nao como texto;</li>
 *   <li>diz em nome de quem age ({@code onBehalfOf}) e de quem veio ({@code delegatedFrom});</li>
 *   <li>e amarrado a uma task especifica ({@code taskId}), entao nao pode ser reaproveitado
 *       em outra operacao;</li>
 *   <li>pode ser de uso unico ({@code singleUse}).</li>
 * </ul>
 *
 * @param jti           identificador unico do token (usado para consumir tokens single-use)
 * @param issuer        quem emitiu
 * @param subject       o agente que porta o token
 * @param audience      o agente para quem o token foi emitido (o proximo salto)
 * @param onBehalfOf    o principal humano/de negocio, ex. {@code customer:99182}
 * @param delegatedFrom o agente que delegou (vazio no token de borda)
 * @param scope         o escopo unico deste token
 * @param maxAmount     teto de valor, ou {@code null} quando o escopo nao move dinheiro
 * @param singleUse     se verdadeiro, o {@code jti} so pode ser aceito uma vez
 * @param taskId        a task A2A a que este token esta amarrado
 * @param issuedAt      emissao
 * @param expiresAt     expiracao (curta, de proposito)
 * @param raw           a forma serializada (JWT compacto) que viaja no header Authorization
 */
public record ScopeToken(String jti,
                         String issuer,
                         String subject,
                         String audience,
                         String onBehalfOf,
                         @Nullable String delegatedFrom,
                         String scope,
                         @Nullable BigDecimal maxAmount,
                         boolean singleUse,
                         String taskId,
                         Instant issuedAt,
                         Instant expiresAt,
                         String raw) {

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Um resumo de uma linha, do jeito que aparece no log governado. */
    public String describe() {
        return "scope=" + scope
                + " on_behalf_of=" + onBehalfOf
                + (delegatedFrom == null ? "" : " delegated_from=" + delegatedFrom);
    }
}
