package br.com.talk.a2a.demoa;

import java.time.Duration;
import java.util.List;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Fiacao da Demo A.
 *
 * <p>Tres beans, e acabou: o modelo, o AiService e o Agent Card. Nada de politica,
 * nada de registry, nada de token. E exatamente esse o ponto — da para colocar um agente
 * A2A no ar em uma tarde, e nada no caminho feliz te obriga a responder "quem autorizou?".
 */
@Configuration
public class NaiveAgentConfiguration {

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
    public RefundAgent refundAgent(ChatModel chatModel, RefundTools tools) {
        return AiServices.builder(RefundAgent.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    /**
     * O Agent Card da Demo A.
     *
     * <p>Note {@code securitySchemes} e {@code securityRequirements} ausentes e a ausencia de
     * qualquer {@code signatures}: o card e apenas um JSON servido em
     * {@code /.well-known/agent-card.json}. Qualquer um imprime um igual.
     */
    @Bean
    public AgentCard agentCard(@Value("${a2a.agent.url:http://localhost:8080/}") String url) {
        return AgentCard.builder()
                .name("refund-agent")
                .description("Agente de estorno do banco. Demo A da palestra "
                        + "\"Quem autorizou esse agente?\" - deliberadamente ingenuo.")
                .version("1.0.0")
                .provider(new AgentProvider("Banco Demo", "https://example.invalid"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text/plain"))
                .defaultOutputModes(List.of("text/plain"))
                .skills(List.of(AgentSkill.builder()
                        .id("refund")
                        .name("Estorno")
                        .description("Avalia e executa o estorno de uma cobranca para um cliente.")
                        .tags(List.of("banking", "refund"))
                        .examples(List.of("Estornar a cobranca duplicada de R$ 4.812,00 do cliente 99182"))
                        // Sem securityRequirements: a skill que move dinheiro nao exige escopo nenhum.
                        .build()))
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(),
                        url,
                        null,
                        AgentInterface.CURRENT_PROTOCOL_VERSION)))
                .url(url)
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
    }

    @Bean
    public AgentExecutor agentExecutor(RefundAgent refundAgent, RefundLedger ledger) {
        return new NaiveRefundAgentExecutor(refundAgent, ledger);
    }
}
