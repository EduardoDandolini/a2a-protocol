package br.com.talk.a2a.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentCardSignature;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;

/**
 * Assina um {@link AgentCard} com JWS sobre a forma canonicalizada (JCS) do card.
 *
 * <p>Lacuna 1 da palestra: "Agent Card nao e cracha. E cartao de visita — e qualquer um
 * imprime um." A mitigacao e assinar o card e verificar a assinatura antes de qualquer chamada.
 *
 * <p>A assinatura cobre o card SEM o campo {@code signatures}, canonicalizado. A verificacao
 * ({@link AgentCardVerifier}) roda sobre o TEXTO JSON recebido, e nao sobre um objeto
 * reconstruido — ver {@link JsonCanonicalizer#canonicalizeWithout}.
 *
 * <p><b>Simplificacao de demo:</b> HMAC-SHA256 com segredo compartilhado, no lugar de uma PKI
 * com chaves assimetricas, {@code kid}, JWKS publicado e rotacao. A forma do artefato (JWS
 * destacada sobre JCS, guardada em {@link AgentCard#signatures()}) e a mesma.
 */
public class AgentCardSigner {

    /** O campo do card que a assinatura NAO cobre (porque a contem). */
    public static final String SIGNATURES_MEMBER = "signatures";

    private final byte[] secret;
    private final String keyId;

    public AgentCardSigner(String secret, String keyId) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "O segredo de assinatura do card precisa de pelo menos 32 bytes (HS256).");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.keyId = keyId;
    }

    /** Devolve uma copia do card com a assinatura JWS anexada em {@code signatures}. */
    public AgentCard sign(AgentCard card) {
        AgentCard unsigned = AgentCard.builder(card).signatures(List.of()).build();
        String canonical = canonicalFormOf(toJson(unsigned));

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(keyId).build();
        JWSObject jws = new JWSObject(header, new Payload(canonical));
        try {
            jws.sign(new MACSigner(new SecretKeySpec(secret, "HmacSHA256")));
        } catch (JOSEException e) {
            throw new IllegalStateException("Falha ao assinar o Agent Card", e);
        }

        // JWS destacada: guardamos header protegido + assinatura, e nao o payload
        // (o payload E o proprio card, servido em /.well-known/agent-card.json).
        AgentCardSignature signature = AgentCardSignature.builder()
                .protectedHeader(jws.getHeader().toBase64URL().toString())
                .signature(jws.getSignature().toString())
                .build();

        return AgentCard.builder(card).signatures(List.of(signature)).build();
    }

    /** A forma canonicalizada (JCS) do JSON de um card, sem o campo {@code signatures}. */
    public static String canonicalFormOf(String cardJson) {
        return JsonCanonicalizer.canonicalizeWithout(cardJson, SIGNATURES_MEMBER);
    }

    public static String toJson(AgentCard card) {
        try {
            return JsonUtil.toJson(card);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Nao foi possivel serializar o Agent Card", e);
        }
    }

    /**
     * Fingerprint SHA-256 da forma canonicalizada do card (sem assinaturas).
     *
     * <p>E o valor que o {@link AgentRegistry} guarda para cada agente confiavel: mesmo que a
     * assinatura confira, o card so vale se for o card que voce esperava.
     */
    public static String fingerprint(String cardJson) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonicalFormOf(cardJson).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel", e);
        }
    }

    public static String fingerprint(AgentCard card) {
        return fingerprint(toJson(card));
    }
}
