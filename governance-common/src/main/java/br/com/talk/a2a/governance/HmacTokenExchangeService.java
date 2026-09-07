package br.com.talk.a2a.governance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Implementacao de demo do {@link TokenExchangeService}: JWT assinado com HMAC-SHA256 e
 * estado de consumo em memoria.
 *
 * <p><b>Simplificacoes conscientes desta demo</b> (leia tambem a secao correspondente do
 * README): um segredo HMAC compartilhado no lugar de um IdP com chaves assimetricas e
 * rotacao; o conjunto de {@code jti} consumidos vive num {@link ConcurrentHashMap} em vez de
 * um armazenamento compartilhado. O que NAO e simplificado e a semantica: escopo unico por
 * token, teto de valor numerico, vinculo com a task, TTL curto e atenuacao monotonica.
 *
 * <p>Trocar por OAuth 2.0 Token Exchange (RFC 8693) e substituir esta classe, nao o resto.
 */
public class HmacTokenExchangeService implements TokenExchangeService {

    private static final Logger LOG = LoggerFactory.getLogger(HmacTokenExchangeService.class);

    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_MAX_AMOUNT = "max_amount";
    private static final String CLAIM_SINGLE_USE = "single_use";
    private static final String CLAIM_TASK_ID = "task_id";
    private static final String CLAIM_ON_BEHALF_OF = "on_behalf_of";
    private static final String CLAIM_DELEGATED_FROM = "delegated_from";

    private final String issuer;
    private final byte[] secret;
    private final Duration ttl;
    private final Set<String> consumedJtis = ConcurrentHashMap.newKeySet();

    public HmacTokenExchangeService(String issuer, String secret, Duration ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "O segredo HMAC da demo precisa de pelo menos 32 bytes (HS256).");
        }
        this.issuer = issuer;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
    }

    @Override
    public ScopeToken issue(String subject,
                            String audience,
                            String onBehalfOf,
                            String scope,
                            @Nullable BigDecimal maxAmount,
                            boolean singleUse,
                            String taskId) {
        return sign(new ScopeToken(UUID.randomUUID().toString(), issuer, subject, audience,
                onBehalfOf, null, scope, maxAmount, singleUse, taskId,
                Instant.now(), Instant.now().plus(ttl), ""));
    }

    /**
     * Atenuacao. O ponto inteiro desta classe.
     *
     * <p>Recusa qualquer troca que AMPLIE privilegio: nao da para subir o teto de valor,
     * nao da para trocar de task, e nao da para tirar o {@code single_use} de um token que
     * ja era de uso unico. Um agente comprometido nao consegue pedir mais do que recebeu.
     */
    @Override
    public ScopeToken attenuate(ScopeToken current,
                                String audience,
                                String scope,
                                @Nullable BigDecimal maxAmount,
                                boolean singleUse) {
        if (current.maxAmount() != null
                && (maxAmount == null || maxAmount.compareTo(current.maxAmount()) > 0)) {
            throw AuthorizationDeniedException.forbidden(
                    "Atenuacao invalida: o token de destino tem teto maior que o de origem ("
                            + maxAmount + " > " + current.maxAmount() + ")");
        }
        if (current.singleUse() && !singleUse) {
            throw AuthorizationDeniedException.forbidden(
                    "Atenuacao invalida: nao e possivel remover single_use de um token de uso unico");
        }

        ScopeToken next = new ScopeToken(UUID.randomUUID().toString(), issuer,
                current.audience(),          // quem porta o novo token e quem recebeu o anterior
                audience,
                current.onBehalfOf(),        // o principal humano nao muda ao longo da cadeia
                current.audience(),          // delegated_from = este agente
                scope,
                maxAmount,
                singleUse,
                current.taskId(),            // amarrado a MESMA task
                Instant.now(),
                Instant.now().plus(ttl),
                "");

        LOG.info("token exchange: {} -> {} (task={}, de {} para {})",
                current.scope(), scope, current.taskId(), current.audience(), audience);
        return sign(next);
    }

    @Override
    public ScopeToken verify(String rawToken, String expectedAudience) {
        SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(rawToken);
        } catch (ParseException e) {
            throw AuthorizationDeniedException.unauthenticated("Token malformado");
        }

        try {
            if (!jwt.verify(new MACVerifier(secret))) {
                throw AuthorizationDeniedException.unauthenticated("Assinatura do token invalida");
            }
        } catch (JOSEException e) {
            throw AuthorizationDeniedException.unauthenticated("Nao foi possivel verificar o token");
        }

        JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw AuthorizationDeniedException.unauthenticated("Claims do token ilegiveis");
        }

        if (!issuer.equals(claims.getIssuer())) {
            throw AuthorizationDeniedException.unauthenticated(
                    "Emissor desconhecido: " + claims.getIssuer());
        }
        if (claims.getExpirationTime() == null
                || Instant.now().isAfter(claims.getExpirationTime().toInstant())) {
            throw AuthorizationDeniedException.unauthenticated("Token expirado");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience)) {
            throw AuthorizationDeniedException.forbidden(
                    "Token emitido para outro destinatario: " + claims.getAudience()
                            + " (esperado " + expectedAudience + ")");
        }

        try {
            String maxAmount = claims.getStringClaim(CLAIM_MAX_AMOUNT);
            return new ScopeToken(
                    claims.getJWTID(),
                    claims.getIssuer(),
                    claims.getSubject(),
                    expectedAudience,
                    claims.getStringClaim(CLAIM_ON_BEHALF_OF),
                    claims.getStringClaim(CLAIM_DELEGATED_FROM),
                    claims.getStringClaim(CLAIM_SCOPE),
                    maxAmount == null ? null : new BigDecimal(maxAmount),
                    Boolean.TRUE.equals(claims.getBooleanClaim(CLAIM_SINGLE_USE)),
                    claims.getStringClaim(CLAIM_TASK_ID),
                    claims.getIssueTime().toInstant(),
                    claims.getExpirationTime().toInstant(),
                    rawToken);
        } catch (ParseException e) {
            throw AuthorizationDeniedException.unauthenticated("Claims do token inconsistentes");
        }
    }

    @Override
    public void consume(ScopeToken token) {
        if (!token.singleUse()) {
            return;
        }
        if (!consumedJtis.add(token.jti())) {
            throw AuthorizationDeniedException.forbidden(
                    "Token de uso unico ja consumido (jti=" + token.jti() + ")");
        }
    }

    private ScopeToken sign(ScopeToken token) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .jwtID(token.jti())
                .issuer(token.issuer())
                .subject(token.subject())
                .audience(token.audience())
                .issueTime(Date.from(token.issuedAt()))
                .expirationTime(Date.from(token.expiresAt()))
                .claim(CLAIM_SCOPE, token.scope())
                .claim(CLAIM_SINGLE_USE, token.singleUse())
                .claim(CLAIM_TASK_ID, token.taskId())
                .claim(CLAIM_ON_BEHALF_OF, token.onBehalfOf());

        if (token.maxAmount() != null) {
            claims.claim(CLAIM_MAX_AMOUNT, token.maxAmount().toPlainString());
        }
        if (token.delegatedFrom() != null) {
            claims.claim(CLAIM_DELEGATED_FROM, token.delegatedFrom());
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(new MACSigner(new SecretKeySpec(secret, "HmacSHA256")));
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o token delegado", e);
        }

        return new ScopeToken(token.jti(), token.issuer(), token.subject(), token.audience(),
                token.onBehalfOf(), token.delegatedFrom(), token.scope(), token.maxAmount(),
                token.singleUse(), token.taskId(), token.issuedAt(), token.expiresAt(),
                jwt.serialize());
    }
}
