package br.com.talk.a2a.governance;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.talk.a2a.spring.ServerCallContextFactory;

/**
 * Beans de governanca compartilhados pelos tres agentes da Demo B.
 *
 * <p>Cada agente importa esta configuracao e acrescenta o proprio
 * {@link AuthorizationInterceptor} (com o nome e a skill dele) e o proprio
 * {@link ScopeTokenTaskAuthorizationProvider}.
 */
@Configuration
public class GovernanceConfiguration {

    /**
     * Emissor/verificador de tokens delegados.
     *
     * <p>Simplificacao: um segredo HMAC compartilhado entre os tres agentes, vindo de
     * configuracao. Substituir por um IdP real (OAuth 2.0 Token Exchange, RFC 8693) e trocar
     * esta implementacao de {@link TokenExchangeService}, nada mais.
     */
    @Bean
    public TokenExchangeService tokenExchangeService(
            @Value("${governance.token.issuer:demo-idp}") String issuer,
            @Value("${governance.token.secret}") String secret,
            @Value("${governance.token.ttl-seconds:60}") long ttlSeconds) {
        return new HmacTokenExchangeService(issuer, secret, Duration.ofSeconds(ttlSeconds));
    }

    @Bean
    public PolicyEngine policyEngine() {
        return PolicyEngine.refundChain();
    }

    @Bean
    public AgentCardSigner agentCardSigner(
            @Value("${governance.card.secret}") String secret,
            @Value("${governance.card.key-id:demo-card-key}") String keyId) {
        return new AgentCardSigner(secret, keyId);
    }

    @Bean
    public AgentCardVerifier agentCardVerifier(@Value("${governance.card.secret}") String secret) {
        return new AgentCardVerifier(secret);
    }

    @Bean
    public IdempotencyStore idempotencyStore() {
        return new InMemoryIdempotencyStore();
    }

    @Bean
    public CompensationLog compensationLog() {
        return new InMemoryCompensationLog();
    }

    @Bean
    public ServerCallContextFactory governedServerCallContextFactory() {
        return new GovernedServerCallContextFactory();
    }
}
