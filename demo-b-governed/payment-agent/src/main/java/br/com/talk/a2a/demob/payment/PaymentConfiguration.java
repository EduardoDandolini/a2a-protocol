package br.com.talk.a2a.demob.payment;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.talk.a2a.governance.AgentCardSigner;
import br.com.talk.a2a.governance.AuthorizationInterceptor;
import br.com.talk.a2a.governance.CompensationLog;
import br.com.talk.a2a.governance.GovernedAgentCards;
import br.com.talk.a2a.governance.IdempotencyStore;
import br.com.talk.a2a.governance.PolicyEngine;
import br.com.talk.a2a.governance.ScopeTokenTaskAuthorizationProvider;
import br.com.talk.a2a.governance.Scopes;
import br.com.talk.a2a.governance.TokenExchangeService;

@Configuration
public class PaymentConfiguration implements WebMvcConfigurer {

    public static final String AGENT_NAME = "payment-agent";

    private final TokenExchangeService tokenExchange;
    private final PolicyEngine policyEngine;

    public PaymentConfiguration(TokenExchangeService tokenExchange, PolicyEngine policyEngine) {
        this.tokenExchange = tokenExchange;
        this.policyEngine = policyEngine;
    }

    /**
     * Checklist item 3 e 4. Na borda do agente que move dinheiro, a exigencia e
     * {@code payment:execute:max_5000} + {@code single_use} — e ela e verificada antes de o
     * SDK criar a task.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizationInterceptor(
                        tokenExchange, policyEngine, AGENT_NAME, Scopes.SKILL_PAYMENT_EXECUTE))
                .addPathPatterns("/");
    }

    @Bean
    public TaskAuthorizationProvider taskAuthorizationProvider() {
        return new ScopeTokenTaskAuthorizationProvider(policyEngine, Scopes.SKILL_PAYMENT_EXECUTE);
    }

    /**
     * O payment-agent e a ponta da cadeia: nao chama ninguem, entao nao tem registry.
     * Zero descoberta dinamica aqui e trivial — nao ha para onde descobrir.
     */
    @Bean
    public AgentCard agentCard(@Value("${a2a.agent.url:http://localhost:8083/}") String url,
                               AgentCardSigner signer) {
        return GovernedAgentCards.build(
                AGENT_NAME,
                "Execucao de pagamentos. Terceiro e ultimo salto da cadeia governada.",
                url,
                Scopes.SKILL_PAYMENT_EXECUTE,
                "Executar estorno",
                "Executa o lancamento do estorno, com idempotencia por task ID e compensacao.",
                Scopes.PAYMENT_EXECUTE_MAX_5000,
                signer);
    }

    @Bean
    public AgentExecutor agentExecutor(PaymentLedger ledger,
                                       IdempotencyStore idempotencyStore,
                                       CompensationLog compensationLog) {
        return new PaymentExecutor(ledger, policyEngine, idempotencyStore, compensationLog,
                tokenExchange);
    }
}
