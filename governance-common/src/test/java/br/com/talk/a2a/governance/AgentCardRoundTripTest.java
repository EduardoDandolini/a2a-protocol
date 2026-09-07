package br.com.talk.a2a.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

/**
 * A verificacao so vale se sobreviver ao caminho real: o card e assinado em memoria, servido
 * como JSON em {@code /.well-known/agent-card.json}, buscado pelo outro agente e desserializado.
 *
 * <p>Se a canonicalizacao nao for estavel nesse ida e volta, a assinatura "falha" sem que nada
 * esteja errado — e a reacao natural do time e desligar a verificacao. Este teste existe para
 * garantir que isso nao aconteca.
 */
class AgentCardRoundTripTest {

    private static final String SECRET = "demo-card-secret-com-32-bytes-ou-mais!!!";

    @Test
    void aAssinaturaSobreviveAoIdaEVoltaEmJson() throws Exception {
        AgentCardSigner signer = new AgentCardSigner(SECRET, "demo-card-key");
        AgentCardVerifier verifier = new AgentCardVerifier(SECRET);

        AgentCard original = GovernedAgentCards.build("credit-agent", "analise",
                "http://localhost:8082/", Scopes.SKILL_REFUND_APPROVE, "Aprovar", "aprova",
                Scopes.REFUND_APPROVE_MAX_5000, signer);

        // Isto e literalmente o que o outro agente recebe no corpo HTTP.
        String served = JsonUtil.toJson(original);

        assertTrue(verifier.verifyJson(served),
                "o JSON servido em /.well-known/agent-card.json precisa verificar");

        // E o card desserializado continua utilizavel.
        AgentCard fetched = JsonUtil.fromJson(served, AgentCard.class);
        assertEquals("credit-agent", fetched.name());
        assertEquals(AgentCardSigner.fingerprint(original), AgentCardSigner.fingerprint(served));

        // Uma virgula a mais no texto, e a assinatura cai.
        String adulterado = served.replace("http://localhost:8082/", "http://atacante.invalid/");
        assertTrue(!verifier.verifyJson(adulterado), "card adulterado no transito nao pode verificar");
    }
}
