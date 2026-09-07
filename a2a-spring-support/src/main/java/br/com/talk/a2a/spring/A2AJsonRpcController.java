package br.com.talk.a2a.spring;

import jakarta.servlet.http.HttpServletRequest;

import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AErrorResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.A2AResponse;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.GetTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.SendMessageRequest;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.A2AMethods;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.spec.InvalidRequestError;
import org.a2aproject.sdk.spec.JSONParseError;
import org.a2aproject.sdk.spec.MethodNotFoundError;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;
import org.a2aproject.sdk.transport.jsonrpc.handler.JSONRPCHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * Endpoint JSON-RPC 2.0 do A2A, exposto como um {@code @RestController} do Spring MVC.
 *
 * <p>Substitui as rotas Vert.x do modulo de referencia Quarkus do SDK
 * ({@code org.a2aproject.sdk.server.apps.quarkus.A2AServerRoutes}), delegando para o mesmo
 * {@link JSONRPCHandler}. Escolhemos um {@code @RestController} sobre um
 * {@code ServletRegistrationBean} porque assim ganhamos de graca os interceptors do
 * Spring MVC — e e num deles que a Demo B pendura a autorizacao de borda.
 *
 * <p>Os tipos do A2A sao serializados com Gson (usam {@code @SerializedName}), entao o corpo
 * entra e sai como {@code String} passando pelo {@link JsonUtil} do SDK, e nao pelo Jackson.
 *
 * <p>Escopo suportado: os metodos sincronos do roteiro da palestra
 * ({@code SendMessage}, {@code GetTask}, {@code CancelTask}, {@code ListTasks}).
 * Streaming por SSE ({@code SendStreamingMessage}, {@code SubscribeToTask}) fica de fora:
 * o Agent Card destes agentes declara {@code streaming=false}.
 */
@RestController
public class A2AJsonRpcController {

    private static final Logger LOG = LoggerFactory.getLogger(A2AJsonRpcController.class);

    private final JSONRPCHandler jsonRpcHandler;
    private final AgentCard agentCard;
    private final ServerCallContextFactory callContextFactory;

    /**
     * A {@link ServerCallContextFactory} e opcional: sem uma, vale a
     * {@link DefaultServerCallContextFactory} (que e o comportamento da Demo A — ninguem
     * pergunta quem chamou). A Demo B fornece a sua, e e por ela que o token verificado
     * chega ao executor.
     */
    public A2AJsonRpcController(JSONRPCHandler jsonRpcHandler,
                                AgentCard agentCard,
                                org.springframework.beans.factory.ObjectProvider<ServerCallContextFactory>
                                        callContextFactory) {
        this.jsonRpcHandler = jsonRpcHandler;
        this.agentCard = agentCard;
        this.callContextFactory =
                callContextFactory.getIfAvailable(DefaultServerCallContextFactory::new);
    }

    /**
     * Agent Card publico.
     *
     * <p>"Agent Card nao e cracha. E cartao de visita — e qualquer um imprime um."
     * Este endpoint e descoberta, nao confianca. Quem consome precisa verificar a assinatura
     * (Demo B: {@code AgentCardVerifier}) e so falar com URLs de um registry curado
     * (Demo B: {@code AgentRegistry}).
     */
    @GetMapping(value = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> agentCard() throws JsonProcessingException {
        return ResponseEntity.ok(JsonUtil.toJson(agentCard));
    }

    @PostMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handle(@RequestBody String body, HttpServletRequest httpRequest) {
        ServerCallContext context = callContextFactory.build(httpRequest);
        A2AResponse<?> response;
        try {
            response = dispatch(body, context);
        } catch (A2AError error) {
            response = new A2AErrorResponse(error);
        } catch (JsonSyntaxException e) {
            response = new A2AErrorResponse(new JSONParseError(e.getMessage()));
        } catch (JsonProcessingException e) {
            response = new A2AErrorResponse(new InvalidRequestError(null, e.getMessage(), null));
        } catch (Throwable t) {
            LOG.error("Erro interno processando requisicao JSON-RPC", t);
            response = new A2AErrorResponse(new InternalError("Internal error"));
        }
        return ResponseEntity.ok(serialize(response));
    }

    private A2AResponse<?> dispatch(String body, ServerCallContext context)
            throws JsonProcessingException {
        JsonObject envelope = JsonParser.parseString(body).getAsJsonObject();
        if (!envelope.has("method")) {
            return new A2AErrorResponse(new InvalidRequestError(null, "missing 'method'", null));
        }
        String method = envelope.get("method").getAsString();
        context.getState().put(JSONRPCContextKeys.METHOD_NAME_KEY, method);

        // Contorna uma peculiaridade do SDK cliente oficial: mesmo no transporte JSON-RPC,
        // SendMessage e serializado atraves do tipo protobuf gerado (ver
        // JSONRPCTransport#sendMessage -> ProtoUtils.ToProto.sendMessageRequest). Como proto3
        // nao distingue "campo ausente" de "string vazia", um Message.taskId() nulo — o caso
        // normal de "estou criando uma task nova" — chega na malha como "taskId":"" em vez de
        // simplesmente nao aparecer. O DefaultRequestHandler do SDK, porem, decide entre
        // checkCreate/checkWrite testando so `taskId != null`: uma string vazia cai no ramo
        // "mensagem para uma task EXISTENTE", tenta achar a task "" no TaskStore, nao encontra,
        // e devolve TaskNotFoundError — mesmo com a autorizacao de criacao ja tendo passado.
        // E exatamente o que quebrava o salto atendimento -> credito da Demo B: o
        // A2AOutboundClient nunca define taskId (a task ainda nao existe do lado de la), mas o
        // client do SDK "inventava" um "" na serializacao. Normalizamos aqui, na borda, antes
        // de qualquer coisa do SDK ver o payload.
        normalizeEmptyTaskId(envelope);
        String normalizedBody = envelope.toString();

        return switch (method) {
            case A2AMethods.SEND_MESSAGE_METHOD ->
                    jsonRpcHandler.onMessageSend(JsonUtil.fromJson(normalizedBody, SendMessageRequest.class), context);
            case A2AMethods.GET_TASK_METHOD ->
                    jsonRpcHandler.onGetTask(JsonUtil.fromJson(normalizedBody, GetTaskRequest.class), context);
            case A2AMethods.CANCEL_TASK_METHOD ->
                    jsonRpcHandler.onCancelTask(JsonUtil.fromJson(normalizedBody, CancelTaskRequest.class), context);
            case A2AMethods.LIST_TASK_METHOD ->
                    jsonRpcHandler.onListTasks(JsonUtil.fromJson(normalizedBody, ListTasksRequest.class), context);
            default -> new A2AErrorResponse(
                    envelope.has("id") ? envelope.get("id").getAsString() : null,
                    new MethodNotFoundError(null, "Unsupported method: " + method, null));
        };
    }

    /**
     * Remove {@code params.message.taskId} quando vier como string vazia, restaurando a
     * semantica de "task nova" que o cliente Java tinha antes de passar pela conversao
     * protobuf interna do SDK. Ver o comentario em {@link #dispatch}.
     */
    private static void normalizeEmptyTaskId(JsonObject envelope) {
        if (!envelope.has("params") || !envelope.get("params").isJsonObject()) {
            return;
        }
        JsonObject params = envelope.getAsJsonObject("params");
        if (!params.has("message") || !params.get("message").isJsonObject()) {
            return;
        }
        JsonObject message = params.getAsJsonObject("message");
        if (message.has("taskId") && message.get("taskId").isJsonPrimitive()
                && message.get("taskId").getAsJsonPrimitive().isString()
                && message.get("taskId").getAsString().isEmpty()) {
            message.remove("taskId");
        }
    }

    private String serialize(A2AResponse<?> response) {
        try {
            return JsonUtil.toJson(response);
        } catch (JsonProcessingException e) {
            LOG.error("Falha ao serializar resposta JSON-RPC", e);
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }
}
