package br.com.talk.a2a.governance;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autorizacao na BORDA, antes de a task existir.
 *
 * <p>Checklist item 4: "Autorizacao antes de aceitar a task, nao dentro da tool."
 *
 * <p>Este {@link HandlerInterceptor} do Spring MVC roda antes do {@code @RestController} que
 * expoe o endpoint JSON-RPC, ou seja: antes do {@code JSONRPCHandler}, antes do
 * {@code DefaultRequestHandler}, antes de a task ir para {@code submitted}, e muito antes de
 * qualquer {@code @Tool} rodar. Se o token nao existe, nao confere, expirou, e de outra
 * audiencia, ou nao traz o escopo que esta skill exige, a requisicao morre aqui com 401/403
 * e a task nunca chega a existir.
 *
 * <p>Compare com a Demo A, onde a autenticacao (quando existe) acontece em t0 e a autorizacao,
 * se acontece, acontece em t3 dentro da tool. O intervalo entre t0 e t3 e onde o incidente mora.
 *
 * <p>Esta e a primeira de tres barreiras, todas usando o MESMO {@link PolicyEngine}:
 * <ol>
 *   <li>aqui, na borda HTTP (esta classe);</li>
 *   <li>no {@link ScopeTokenTaskAuthorizationProvider}, hook nativo do SDK chamado pelo
 *       {@code DefaultRequestHandler} antes do aceite da task;</li>
 *   <li>no executor, imediatamente antes do efeito colateral, junto com a idempotencia.</li>
 * </ol>
 * Defesa em profundidade: nenhuma delas depende do LLM ter se comportado.
 */
public class AuthorizationInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(AuthorizationInterceptor.class);

    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenExchangeService tokenExchange;
    private final PolicyEngine policyEngine;
    private final String agentName;
    private final String skillId;

    public AuthorizationInterceptor(TokenExchangeService tokenExchange,
                                    PolicyEngine policyEngine,
                                    String agentName,
                                    String skillId) {
        this.tokenExchange = tokenExchange;
        this.policyEngine = policyEngine;
        this.agentName = agentName;
        this.skillId = skillId;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        request.setAttribute(GovernanceContextKeys.TRACE_ID, traceId);
        request.setAttribute(GovernanceContextKeys.SKILL_ID, skillId);

        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                throw AuthorizationDeniedException.unauthenticated(
                        "Chamada sem bearer token. Toda borda de agente exige mTLS ou OAuth2.");
            }

            // 1. o token e valido, nao expirou, e foi emitido PARA MIM?
            ScopeToken token = tokenExchange.verify(
                    authorization.substring(BEARER_PREFIX.length()).trim(), agentName);

            // 2. o escopo que ele carrega satisfaz a skill que este agente expoe?
            //    (o valor so pode ser conferido depois de ler o corpo; a checagem final de
            //     valor acontece no executor, com o MESMO PolicyEngine)
            policyEngine.authorize(token, skillId, token.maxAmount()).orThrow();

            request.setAttribute(GovernanceContextKeys.SCOPE_TOKEN, token);
            return true;

        } catch (AuthorizationDeniedException denied) {
            LOG.warn("trace={} agent={} skill={} AUTORIZACAO NEGADA na borda: {}",
                    traceId, agentName, skillId, denied.getMessage());
            response.setStatus(denied.status());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"" + escape(denied.getMessage()) + "\"}");
            return false;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
