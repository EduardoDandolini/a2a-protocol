package br.com.talk.a2a.demob.customer;

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
public class CustomerServiceConfiguration implements WebMvcConfigurer {

    public static final String AGENT_NAME = "customer-service-agent";

    private final TokenExchangeService tokenExchange;
    private final PolicyEngine policyEngine;

    public CustomerServiceConfiguration(TokenExchangeService tokenExchange, PolicyEngine policyEngine) {
        this.tokenExchange = tokenExchange;
        this.policyEngine = policyEngine;
    }

    /**
     * Checklist item 4. O interceptor roda ANTES do controller JSON-RPC, ou seja: antes de
     * o SDK sequer olhar o corpo da requisicao, antes de a task ser criada.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthorizationInterceptor(
                        tokenExchange, policyEngine, AGENT_NAME, Scopes.SKILL_REFUND_REQUEST))
                .addPathPatterns("/");
    }

    /** Checklist item 4, segunda barreira: hook nativo do SDK, antes do aceite da task. */
    @Bean
    public TaskAuthorizationProvider taskAuthorizationProvider() {
        return new ScopeTokenTaskAuthorizationProvider(policyEngine, Scopes.SKILL_REFUND_REQUEST);
    }

    /**
     * Checklist item 1: registry CURADO. A URL do credit-agent vem da configuracao,
     * nunca de um card encontrado na rede.
     */
    @Bean
    public AgentRegistry agentRegistry(AgentCardVerifier verifier,
                                       @Value("${governance.registry.credit-agent-url}") String creditUrl) {
        return new AgentRegistry(verifier)
                .trust(new AgentRegistry.TrustedAgent("credit-agent", creditUrl, null));
    }

    @Bean
    public AgentCard agentCard(@Value("${a2a.agent.url:http://localhost:8081/}") String url,
                               AgentCardSigner signer) {
        return GovernedAgentCards.build(
                AGENT_NAME,
                "Atendimento ao cliente. Primeiro salto da cadeia governada de estorno.",
                url,
                Scopes.SKILL_REFUND_REQUEST,
                "Solicitar estorno",
                "Recebe o pedido de estorno do cliente e o encaminha para analise de credito.",
                Scopes.REFUND_REQUEST,
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
    public TriageAgent triageAgent(ChatModel chatModel) {
        return AiServices.builder(TriageAgent.class).chatModel(chatModel).build();
    }

    @Bean
    public AgentExecutor agentExecutor(TriageAgent triageAgent, AgentRegistry registry) {
        return new CustomerServiceExecutor(triageAgent, registry, tokenExchange);
    }
}
