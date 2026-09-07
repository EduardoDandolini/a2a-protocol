package br.com.talk.a2a.spring;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;

/**
 * Factory padrao: copia os headers para o estado da chamada e marca o usuario como
 * NAO autenticado.
 *
 * <p>E exatamente este o comportamento da Demo A — e e o ponto da palestra: o agente
 * ingenuo aceita a task sem nunca perguntar quem esta do outro lado.
 */
public class DefaultServerCallContextFactory implements ServerCallContextFactory {

    @Override
    public ServerCallContext build(HttpServletRequest request) {
        Map<String, Object> state = new HashMap<>();
        state.put(JSONRPCContextKeys.HEADERS_KEY, headersOf(request));
        state.put(JSONRPCContextKeys.TENANT_KEY, "");

        Set<String> extensions = A2AExtensions.getRequestedExtensions(
                Collections.list(request.getHeaders(A2AHeaders.A2A_EXTENSIONS)));

        String protocolVersion = request.getHeader(A2AHeaders.A2A_VERSION);

        return new ServerCallContext(UnauthenticatedUser.INSTANCE, state, extensions, protocolVersion);
    }

    /** Copia todos os headers HTTP da requisicao para um mapa simples. */
    public static Map<String, String> headersOf(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        List<String> names = Collections.list(request.getHeaderNames());
        for (String name : names) {
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
