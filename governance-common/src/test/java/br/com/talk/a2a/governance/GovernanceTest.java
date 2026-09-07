package br.com.talk.a2a.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Prova, sem subir nenhum agente, que as tres lacunas estao mesmo fechadas.
 */
class GovernanceTest {

    private static final String SECRET = "demo-token-secret-com-32-bytes-ou-mais!!";
    private static final BigDecimal VALOR = new BigDecimal("4812.00");

    private final TokenExchangeService tokens =
            new HmacTokenExchangeService("demo-idp", SECRET, Duration.ofSeconds(60));
    private final PolicyEngine policy = PolicyEngine.refundChain();

    // -----------------------------------------------------------------------
    // Lacuna 2: autorizacao antes, e por escopo
    // -----------------------------------------------------------------------

    @Test
    void escopoErradoEhNegado() {
        ScopeToken token = tokens.issue("channel", "credit-agent", "customer:99182",
                Scopes.REFUND_REQUEST, null, false, "task-1");

        PolicyDecision decision = policy.authorize(token, Scopes.SKILL_REFUND_APPROVE, VALOR);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("Escopo insuficiente"));
        assertEquals(Scopes.POLICY_ENGINE_ID, decision.policyId());
    }

    @Test
    void semTokenNaoHaDecisaoFavoravel() {
        assertFalse(policy.authorize(null, Scopes.SKILL_PAYMENT_EXECUTE, VALOR).allowed());
    }

    @Test
    void valorAcimaDaAlcadaEhNegadoMesmoComEscopoCerto() {
        ScopeToken token = tokens.issue("credit-agent", "payment-agent", "customer:99182",
                Scopes.PAYMENT_EXECUTE_MAX_5000, Scopes.MAX_AMOUNT, true, "task-1");

        PolicyDecision decision = policy.authorize(token, Scopes.SKILL_PAYMENT_EXECUTE,
                new BigDecimal("50000.00"));

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("acima da alcada"));
    }

    // -----------------------------------------------------------------------
    // Lacuna 3a: escopo atenuado a cada salto
    // -----------------------------------------------------------------------

    @Test
    void cadeiaCompletaAtenuaOEscopoAcadaSalto() {
        ScopeToken borda = tokens.issue("channel:app", "customer-service-agent",
                "customer:99182", Scopes.REFUND_REQUEST, null, false, "a7f3c9");

        ScopeToken paraCredito = tokens.attenuate(borda, "credit-agent",
                Scopes.REFUND_APPROVE_MAX_5000, Scopes.MAX_AMOUNT, false);
        ScopeToken paraPagamento = tokens.attenuate(paraCredito, "payment-agent",
                Scopes.PAYMENT_EXECUTE_MAX_5000, Scopes.MAX_AMOUNT, true);

        assertEquals(Scopes.PAYMENT_EXECUTE_MAX_5000, paraPagamento.scope());
        assertEquals("credit-agent", paraPagamento.delegatedFrom());
        assertEquals("customer:99182", paraPagamento.onBehalfOf());
        assertEquals("a7f3c9", paraPagamento.taskId());
        assertTrue(paraPagamento.singleUse());

        // e o token final so serve para o payment-agent
        assertTrue(policy.authorize(
                tokens.verify(paraPagamento.raw(), "payment-agent"),
                Scopes.SKILL_PAYMENT_EXECUTE, VALOR).allowed());
    }

    @Test
    void atenuacaoNaoPodeAmpliarPrivilegio() {
        ScopeToken restrito = tokens.issue("credit-agent", "payment-agent", "customer:99182",
                Scopes.PAYMENT_EXECUTE_MAX_5000, new BigDecimal("100.00"), true, "task-1");

        assertThrows(AuthorizationDeniedException.class, () -> tokens.attenuate(
                restrito, "payment-agent", Scopes.PAYMENT_EXECUTE_MAX_5000,
                new BigDecimal("999999.00"), true));

        assertThrows(AuthorizationDeniedException.class, () -> tokens.attenuate(
                restrito, "payment-agent", Scopes.PAYMENT_EXECUTE_MAX_5000,
                new BigDecimal("100.00"), false));
    }

    @Test
    void tokenDeOutraAudienciaEhRecusado() {
        ScopeToken token = tokens.issue("channel", "customer-service-agent", "customer:99182",
                Scopes.REFUND_REQUEST, null, false, "task-1");

        assertThrows(AuthorizationDeniedException.class,
                () -> tokens.verify(token.raw(), "payment-agent"));
    }

    @Test
    void tokenDeUsoUnicoSoEhAceitoUmaVez() {
        ScopeToken token = tokens.issue("credit-agent", "payment-agent", "customer:99182",
                Scopes.PAYMENT_EXECUTE_MAX_5000, Scopes.MAX_AMOUNT, true, "task-1");

        tokens.consume(token);
        assertThrows(AuthorizationDeniedException.class, () -> tokens.consume(token));
    }

    // -----------------------------------------------------------------------
    // Lacuna 3b: idempotencia por task ID e compensacao
    // -----------------------------------------------------------------------

    @Test
    void aSegundaExecucaoDaMesmaTaskEhSuprimida() {
        IdempotencyStore store = new InMemoryIdempotencyStore();

        assertTrue(store.reserve("a7f3c9").isEmpty(), "a primeira chamada deve executar");
        store.complete("a7f3c9", "estorno R$ 4.812,00");

        Optional<IdempotencyStore.Execution> segunda = store.reserve("a7f3c9");
        assertTrue(segunda.isPresent(), "a segunda chamada deve ser suprimida");
        assertEquals("estorno R$ 4.812,00", segunda.get().resultSummary());
    }

    @Test
    void falhaLiberaAReservaParaUmRetryLegitimo() {
        IdempotencyStore store = new InMemoryIdempotencyStore();
        assertTrue(store.reserve("task-1").isEmpty());
        store.release("task-1");
        assertTrue(store.reserve("task-1").isEmpty(), "apos falha, um retry deve poder executar");
    }

    @Test
    void todaAcaoComEfeitoColateralRegistraSuaInversa() {
        CompensationLog log = new InMemoryCompensationLog();
        CompensationLog.CompensationEntry entry = log.register(
                new CompensationLog.CompensationEntry("cmp-1", "a7f3c9", "8b1e", "payment-agent",
                        "refund", "reverse_refund", "customer:99182", VALOR, "pmt-1",
                        "customer:99182", Scopes.POLICY_ENGINE_ID,
                        CompensationLog.Status.REGISTERED, java.time.Instant.now()));

        assertEquals("reverse_refund", entry.inverseAction());
        assertTrue(log.findByTaskId("a7f3c9").isPresent());

        log.markCompensated("cmp-1");
        assertEquals(CompensationLog.Status.COMPENSATED,
                log.findByTaskId("a7f3c9").orElseThrow().status());
    }

    // -----------------------------------------------------------------------
    // Lacuna 1: card assinado sobre a forma canonicalizada, e verificado
    // -----------------------------------------------------------------------

    @Test
    void canonicalizacaoIgnoraOrdemDeChavesEEspacos() {
        String a = "{\"b\":1,\"a\":[1,2],\"c\":{\"z\":true,\"y\":\"x\"}}";
        String b = "{ \"c\" : { \"y\" : \"x\", \"z\" : true } , \"a\" : [ 1 , 2 ] , \"b\" : 1 }";
        assertEquals(JsonCanonicalizer.canonicalize(a), JsonCanonicalizer.canonicalize(b));
    }

    @Test
    void cardAssinadoVerificaECardAdulteradoNao() {
        String cardSecret = "demo-card-secret-com-32-bytes-ou-mais!!!";
        AgentCardSigner signer = new AgentCardSigner(cardSecret, "demo-card-key");
        AgentCardVerifier verifier = new AgentCardVerifier(cardSecret);

        var card = GovernedAgentCards.build("payment-agent", "paga", "http://localhost:8083/",
                Scopes.SKILL_PAYMENT_EXECUTE, "Executar", "executa",
                Scopes.PAYMENT_EXECUTE_MAX_5000, signer);

        assertTrue(verifier.verify(card), "o card recem-assinado deve verificar");

        // Alguem reimprime o cartao de visita apontando para outro lugar.
        var forjado = org.a2aproject.sdk.spec.AgentCard.builder(card)
                .url("http://atacante.invalid/")
                .build();

        assertFalse(verifier.verify(forjado), "card adulterado nao pode verificar");
    }

    @Test
    void cardSemAssinaturaNaoEhCracha() {
        AgentCardVerifier verifier = new AgentCardVerifier("demo-card-secret-com-32-bytes-ou-mais!!!");
        AgentCardSigner signer = new AgentCardSigner("demo-card-secret-com-32-bytes-ou-mais!!!", "k");

        var card = GovernedAgentCards.build("x", "x", "http://localhost:1/",
                Scopes.SKILL_PAYMENT_EXECUTE, "x", "x", Scopes.PAYMENT_EXECUTE_MAX_5000, signer);
        var semAssinatura = org.a2aproject.sdk.spec.AgentCard.builder(card)
                .signatures(java.util.List.of())
                .build();

        assertFalse(verifier.verify(semAssinatura));
    }

    @Test
    void registryRecusaAgenteForaDaAllowlist() {
        AgentRegistry registry = new AgentRegistry(
                new AgentCardVerifier("demo-card-secret-com-32-bytes-ou-mais!!!"));

        AuthorizationDeniedException denied = assertThrows(AuthorizationDeniedException.class,
                () -> registry.resolve("agente-que-apareceu-na-rede"));

        assertEquals(403, denied.status());
        assertTrue(denied.getMessage().contains("descoberta dinamica"));
    }
}
