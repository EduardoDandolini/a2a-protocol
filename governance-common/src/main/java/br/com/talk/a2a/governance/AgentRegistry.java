package br.com.talk.a2a.governance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.AgentCard;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry CURADO de agentes confiaveis. Sem descoberta dinamica.
 *
 * <p>Checklist item 1: "Registry curado. Zero descoberta dinamica em producao."
 *
 * <p>A diferenca em relacao a descoberta e a direcao da confianca. Na descoberta dinamica,
 * um agente encontra um card na rede e decide falar com ele. Aqui a lista de com quem se pode
 * falar e definida na configuracao, fora do alcance do LLM e fora do alcance de quem
 * conseguir publicar um card. O card ainda e buscado — para saber COMO chamar — mas so vale
 * se (a) a URL estiver na lista, (b) a assinatura conferir, e (c) o fingerprint bater com o
 * que foi registrado.
 *
 * <p>O ponto (c) e o que impede o "downgrade do card": um agente legitimo cujo card foi
 * trocado por um que aponta para outro lugar continua sendo recusado.
 */
public class AgentRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRegistry.class);

    /**
     * @param logicalName     nome logico usado no codigo, ex. {@code payment-agent}
     * @param url             URL do agente (fixa, vinda de configuracao)
     * @param expectedFingerprint fingerprint esperado do card, ou {@code null} para
     *                            "confio na assinatura mas ainda nao fixei o card" (TOFU)
     */
    public record TrustedAgent(String logicalName, String url, @Nullable String expectedFingerprint) {
    }

    private final Map<String, TrustedAgent> allowlist = new LinkedHashMap<>();
    private final Map<String, AgentCard> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final AgentCardVerifier verifier;

    public AgentRegistry(AgentCardVerifier verifier) {
        this.verifier = verifier;
    }

    public AgentRegistry trust(TrustedAgent agent) {
        allowlist.put(agent.logicalName(), agent);
        return this;
    }

    public Optional<TrustedAgent> lookup(String logicalName) {
        return Optional.ofNullable(allowlist.get(logicalName));
    }

    /**
     * Resolve o Agent Card de um agente confiavel, verificando assinatura e fingerprint.
     *
     * @throws AuthorizationDeniedException se o nome nao estiver na allowlist, se a assinatura
     *                                      nao conferir, ou se o fingerprint divergir
     */
    public AgentCard resolve(String logicalName) {
        TrustedAgent trusted = lookup(logicalName).orElseThrow(() ->
                AuthorizationDeniedException.forbidden(
                        "Agente '" + logicalName + "' nao esta no registry curado. "
                                + "Nao existe descoberta dinamica aqui."));

        AgentCard cached = cache.get(logicalName);
        if (cached != null) {
            return cached;
        }

        // Buscamos o JSON CRU. Verificar a assinatura sobre o texto recebido e obrigatorio:
        // desserializar antes (o resolver do SDK passa o card por protobuf) normalizaria os
        // bytes e quebraria a assinatura sem que nada estivesse errado. Ver AgentCardVerifier.
        String cardJson = fetchCardJson(logicalName, trusted.url());

        verifier.verifyJsonOrThrow(cardJson, logicalName);

        String fingerprint = AgentCardSigner.fingerprint(cardJson);
        if (trusted.expectedFingerprint() != null
                && !trusted.expectedFingerprint().equals(fingerprint)) {
            throw AuthorizationDeniedException.forbidden(
                    "Fingerprint do card de " + logicalName + " divergente. Esperado "
                            + trusted.expectedFingerprint() + ", recebido " + fingerprint);
        }

        AgentCard card;
        try {
            card = JsonUtil.fromJson(cardJson, AgentCard.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Agent Card de " + logicalName + " nao pode ser desserializado", e);
        }

        LOG.info("registry: agente '{}' confiavel em {} (assinatura ok, fingerprint {})",
                logicalName, trusted.url(), fingerprint);
        cache.put(logicalName, card);
        return card;
    }

    /** GET simples em {@code {url}/.well-known/agent-card.json}, devolvendo o corpo cru. */
    private static String fetchCardJson(String logicalName, String baseUrl) {
        String cardUrl = (baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                + ".well-known/agent-card.json";
        try (HttpClient http = HttpClient.newHttpClient()) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(cardUrl))
                            .header("Accept", "application/json")
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Agent Card de " + logicalName + " em " + cardUrl
                        + " respondeu HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Nao foi possivel obter o Agent Card de " + logicalName + " em " + cardUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrompido ao obter o Agent Card de " + logicalName, e);
        }
    }
}
