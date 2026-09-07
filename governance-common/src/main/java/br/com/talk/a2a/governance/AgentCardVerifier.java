package br.com.talk.a2a.governance;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import org.a2aproject.sdk.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.util.Base64URL;

/**
 * Verifica a assinatura JWS de um {@link AgentCard} sobre sua forma canonicalizada (JCS).
 *
 * <p>Checklist item 2: "Agent Card assinado e verificado." O verbo importante e o segundo.
 * Um card assinado que ninguem verifica e um card nao assinado com passos a mais.
 *
 * <p><b>A verificacao roda sobre o TEXTO recebido</b>, nao sobre um objeto desserializado.
 * Isso nao e preciosismo: o proprio resolver de cards do A2A Java SDK
 * ({@code A2ACardResolver}) parseia o JSON para um {@code AgentCard} de PROTOBUF e converte
 * de volta. Qualquer normalizacao nesse caminho mudaria os bytes e faria a assinatura
 * "falhar" com o card intacto — e a reacao natural do time seria desligar a verificacao.
 * Por isso o {@link AgentRegistry} busca o JSON cru, verifica aqui, e so depois converte.
 */
public class AgentCardVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(AgentCardVerifier.class);

    private final byte[] secret;

    public AgentCardVerifier(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verifica a assinatura sobre o JSON cru do card.
     *
     * @param cardJson o corpo devolvido por {@code /.well-known/agent-card.json}
     */
    public boolean verifyJson(String cardJson) {
        JsonObject root;
        try {
            root = JsonParser.parseString(cardJson).getAsJsonObject();
        } catch (RuntimeException e) {
            LOG.warn("Agent Card ilegivel: {}", e.getMessage());
            return false;
        }

        JsonElement signaturesElement = root.get(AgentCardSigner.SIGNATURES_MEMBER);
        if (signaturesElement == null || !signaturesElement.isJsonArray()
                || signaturesElement.getAsJsonArray().isEmpty()) {
            LOG.warn("Agent Card nao traz assinatura. Card nao e cracha: recusando.");
            return false;
        }

        String canonical = AgentCardSigner.canonicalFormOf(cardJson);

        JsonArray signatures = signaturesElement.getAsJsonArray();
        for (JsonElement element : signatures) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject signature = element.getAsJsonObject();
            if (matches(canonical,
                    asString(signature, "protected"),
                    asString(signature, "signature"))) {
                return true;
            }
        }
        LOG.warn("Nenhuma assinatura do Agent Card confere.");
        return false;
    }

    /** Conveniencia para verificar um card ja em memoria (auto-verificacao / testes). */
    public boolean verify(AgentCard card) {
        return verifyJson(AgentCardSigner.toJson(card));
    }

    /** Verifica e lanca 403 quando a assinatura nao confere. */
    public String verifyJsonOrThrow(String cardJson, String agentName) {
        if (!verifyJson(cardJson)) {
            throw AuthorizationDeniedException.forbidden(
                    "Assinatura do Agent Card '" + agentName + "' invalida ou ausente");
        }
        return cardJson;
    }

    private boolean matches(String canonical, String protectedHeader, String signature) {
        if (protectedHeader == null || signature == null) {
            return false;
        }
        try {
            JWSHeader header = JWSHeader.parse(new Base64URL(protectedHeader));
            JWSObject jws = new JWSObject(
                    new Base64URL(protectedHeader),
                    new Payload(canonical).toBase64URL(),
                    new Base64URL(signature));
            return header.getAlgorithm() != null && jws.verify(new MACVerifier(secret));
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            LOG.warn("Assinatura do Agent Card ilegivel: {}", e.getMessage());
            return false;
        }
    }

    private static String asString(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
