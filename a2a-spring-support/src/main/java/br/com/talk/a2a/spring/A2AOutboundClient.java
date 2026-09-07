package br.com.talk.a2a.spring;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.spec.A2AClientException;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Chamada A2A de saida (agente -> agente) sobre JSON-RPC/HTTP real.
 *
 * <p>O token vai no header {@code Authorization} atraves do {@link ClientCallContext} do SDK.
 * Na Demo B, quem monta esse token NUNCA repassa o proprio: cada salto troca o token por um
 * mais estreito ({@code TokenExchangeService}). Ver checklist item 5.
 */
public final class A2AOutboundClient {

    private A2AOutboundClient() {
    }

    /**
     * Envia uma mensagem de texto para outro agente e devolve a {@link Task} resultante.
     *
     * @param card          Agent Card do destino (na Demo B: vindo do registry curado e ja verificado)
     * @param text          conteudo da mensagem
     * @param correlationId o identificador da operacao de negocio, propagado como {@code contextId}.
     *                      NAO enviamos {@code taskId}: em A2A 1.0 o task ID e gerado pelo servidor
     *                      e mandar um id inexistente resulta em {@code TaskNotFound}. Ver
     *                      {@code ChainCorrelation}.
     * @param bearerToken   token delegado, ja atenuado para este salto
     */
    public static Task sendText(AgentCard card,
                                String text,
                                String correlationId,
                                String bearerToken) throws A2AClientException {
        AtomicReference<Task> taskRef = new AtomicReference<>();
        AtomicReference<String> messageRef = new AtomicReference<>();

        java.util.function.BiConsumer<ClientEvent, AgentCard> collector = (event, agentCard) -> {
            if (event instanceof TaskEvent taskEvent) {
                taskRef.set(taskEvent.getTask());
            } else if (event instanceof MessageEvent messageEvent) {
                messageRef.set(textOf(messageEvent.getMessage()));
            }
        };

        Client client = Client.builder(card)
                .clientConfig(new ClientConfig.Builder().setStreaming(false).build())
                .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder())
                .addConsumer(collector)
                .build();

        try {
            Message message = Message.builder(A2A.toUserMessage(text))
                    .contextId(correlationId)
                    .build();

            MessageSendParams params = MessageSendParams.builder()
                    .message(message)
                    .build();

            ClientCallContext callContext = new ClientCallContext(
                    Map.of(),
                    Map.of("Authorization", "Bearer " + bearerToken));

            client.sendMessage(params, List.of(collector), null, callContext);
        } finally {
            client.close();
        }

        Task task = taskRef.get();
        if (task == null) {
            throw new A2AClientException("O agente respondeu com uma Message, nao com uma Task: "
                    + messageRef.get());
        }
        return task;
    }

    /** Concatena o texto de todos os artifacts de uma task. */
    public static String artifactsText(Task task) {
        if (task.artifacts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        task.artifacts().forEach(artifact -> appendParts(sb, artifact.parts()));
        return sb.toString().trim();
    }

    private static String textOf(Message message) {
        StringBuilder sb = new StringBuilder();
        appendParts(sb, message.parts());
        return sb.toString().trim();
    }

    private static void appendParts(StringBuilder sb, List<Part<?>> parts) {
        if (parts == null) {
            return;
        }
        for (Part<?> part : parts) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text()).append('\n');
            }
        }
    }
}
