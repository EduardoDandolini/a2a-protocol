package br.com.talk.a2a.governance;

/**
 * Chaves usadas para carregar o resultado da autorizacao de borda ate o executor,
 * dentro do {@code ServerCallContext} do SDK.
 *
 * <p>Importante: o {@code AgentExecutor} do A2A roda numa thread de background, entao um
 * {@code ThreadLocal} da requisicao HTTP NAO chega la. O estado precisa viajar dentro do
 * {@code ServerCallContext}, que o SDK propaga do transporte ate o
 * {@code RequestContext.getCallContext()}.
 */
public final class GovernanceContextKeys {

    /** O {@link ScopeToken} ja verificado. */
    public static final String SCOPE_TOKEN = "governance.scopeToken";

    /** O trace ID desta cadeia de chamadas. */
    public static final String TRACE_ID = "governance.traceId";

    /** A skill que a requisicao pretende invocar. */
    public static final String SKILL_ID = "governance.skillId";

    private GovernanceContextKeys() {
    }
}
