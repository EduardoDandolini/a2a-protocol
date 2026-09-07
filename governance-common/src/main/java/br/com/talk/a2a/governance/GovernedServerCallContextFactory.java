package br.com.talk.a2a.governance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.AuthenticatedUser;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.transport.jsonrpc.context.JSONRPCContextKeys;

import br.com.talk.a2a.spring.DefaultServerCallContextFactory;
import br.com.talk.a2a.spring.ServerCallContextFactory;

/**
 * Leva o token ja verificado pelo {@link AuthorizationInterceptor} para dentro do
 * {@code ServerCallContext} do SDK.
 *
 * <p>E este o veiculo que faz o resultado da autorizacao de borda chegar ao
 * {@link ScopeTokenTaskAuthorizationProvider} (que roda antes do aceite da task) e ao
 * executor (que roda em outra thread). Nada disso viaja em {@code ThreadLocal}.
 */
public class GovernedServerCallContextFactory implements ServerCallContextFactory {

    @Override
    public ServerCallContext build(HttpServletRequest request) {
        Map<String, Object> state = new HashMap<>();
        state.put(JSONRPCContextKeys.HEADERS_KEY, DefaultServerCallContextFactory.headersOf(request));
        state.put(JSONRPCContextKeys.TENANT_KEY, "");

        Object token = request.getAttribute(GovernanceContextKeys.SCOPE_TOKEN);
        Object traceId = request.getAttribute(GovernanceContextKeys.TRACE_ID);
        Object skillId = request.getAttribute(GovernanceContextKeys.SKILL_ID);

        User user = UnauthenticatedUser.INSTANCE;
        if (token instanceof ScopeToken scopeToken) {
            state.put(GovernanceContextKeys.SCOPE_TOKEN, scopeToken);
            // O "usuario" da task e o principal de negocio em nome de quem se age,
            // nao o agente que fez a chamada HTTP.
            user = new AuthenticatedUser(scopeToken.onBehalfOf());
        }
        if (traceId != null) {
            state.put(GovernanceContextKeys.TRACE_ID, traceId);
        }
        if (skillId != null) {
            state.put(GovernanceContextKeys.SKILL_ID, skillId);
        }

        String protocolVersion = request.getHeader(org.a2aproject.sdk.common.A2AHeaders.A2A_VERSION);
        return new ServerCallContext(user, state, Set.of(), protocolVersion);
    }

    /** Extrai o token verificado de um {@link ServerCallContext}, se houver. */
    public static ScopeToken tokenOf(ServerCallContext context) {
        Object value = context.getState().get(GovernanceContextKeys.SCOPE_TOKEN);
        return value instanceof ScopeToken token ? token : null;
    }

    /** Extrai o trace ID de um {@link ServerCallContext}. */
    public static String traceOf(ServerCallContext context) {
        Object value = context.getState().get(GovernanceContextKeys.TRACE_ID);
        return value instanceof String trace ? trace : "-";
    }

    /** Extrai a skill de um {@link ServerCallContext}. */
    public static String skillOf(ServerCallContext context) {
        Object value = context.getState().get(GovernanceContextKeys.SKILL_ID);
        return value instanceof String skill ? skill : "-";
    }
}
