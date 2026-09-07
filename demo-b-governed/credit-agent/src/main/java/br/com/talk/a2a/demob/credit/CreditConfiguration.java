package br.com.talk.a2a.demob.credit;

import java.time.Duration;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.talk.a2a.governance.AgentCardSigner;
import br.com.talk.a2a.governance.AgentCardVerifier;
import br.com.talk.a2a.governance.AgentRegistry;
import br.com.talk.a2a.governance.AuthorizationInterceptor;
import br.com.talk.a2a.governance.GovernedAgentCards;
import br.com.talk.a2a.governance.PolicyEngine;
import br.com.talk.a2a.governance.ScopeTokenTaskAuthorizationProvider;
import br.com.talk.a2a.governance.Scopes;
import br.com.talk.a2a.governance.TokenExchangeService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;

@Configuration
public class CreditConfiguration implements WebMvcConfigurer {

    public static final String AGENT_NAME = "credit-agent";

    private final TokenExchangeService tokenExchange;
    private final PolicyEngine policyEngine;

    public CreditConfiguration(TokenExchangeService tokenExchange, PolicyEngine policyEngine) {
        this.tokenExchange = tokenExchange;
        this.policyEngine = policyEngine;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizationInterceptor(
                        tokenExchange, policyEngine, AGENT_NAME, Scopes.SKILL_REFUND_APPROVE))
                .addPathPatterns("/");
    }

    @Bean
    public TaskAuthorizationProvider taskAuthorizationProvider() {
        return new ScopeTokenTaskAuthorizationProvider(policyEngine, Scopes.SKILL_REFUND_APPROVE);
    }

    @Bean
    public AgentRegistry agentRegistry(AgentCardVerifier verifier,
                                       @Value("${governance.registry.payment-agent-url}") String paymentUrl) {
        return new AgentRegistry(verifier)
                .trust(new AgentRegistry.TrustedAgent("payment-agent", paymentUrl, null));
    }

    @Bean
    public AgentCard agentCard(@Value("${a2a.agent.url:http://localhost:8082/}") String url,
                               AgentCardSigner signer) {
        return GovernedAgentCards.build(
                AGENT_NAME,
                "Analise de credito. Segundo salto da cadeia governada de estorno.",
                url,
                Scopes.SKILL_REFUND_APPROVE,
                "Aprovar estorno",
                "Avalia e aprova o estorno dentro da alcada, e aciona o pagamento.",
                Scopes.REFUND_APPROVE_MAX_5000,
                signer);
    }

    @Bean
    public ChatModel chatModel(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
                               @Value("${ollama.model-id:llama3.2}") String modelId,
                               @Value("${ollama.timeout-seconds:120}") long timeoutSeconds) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder();
        builder.baseUrl(baseUrl);
        builder.modelName(modelId);
        builder.temperature(0.0);
        builder.timeout(Duration.ofSeconds(timeoutSeconds));
        return builder.build();
    }

    @Bean
    public CreditAnalystAgent creditAnalystAgent(ChatModel chatModel) {
        return AiServices.builder(CreditAnalystAgent.class).chatModel(chatModel).build();
    }

    @Bean
    public AgentExecutor agentExecutor(CreditAnalystAgent analyst, AgentRegistry registry) {
        return new CreditExecutor(analyst, registry, tokenExchange, policyEngine);
    }
}
