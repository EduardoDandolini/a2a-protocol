package br.com.talk.a2a.demob.customer;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.talk.a2a.governance.ScopeToken;
import br.com.talk.a2a.governance.Scopes;
import br.com.talk.a2a.governance.TokenExchangeService;

/**
 * Fica no lugar do IdP, so para a demo rodar com um {@code curl}.
 *
 * <p>Emite o token de BORDA da cadeia: escopo {@code refund:request}, audiencia
 * {@code customer-service-agent}, amarrado a um task ID especifico e valido por poucos
 * segundos.
 *
 * <p><b>Este endpoint nao existiria em producao.</b> Num sistema real o canal de atendimento
 * ja chega com um token do IdP corporativo (OAuth2/OIDC) e nenhum agente emite credencial
 * para si mesmo. Ele esta aqui para que o roteiro da palestra caiba numa linha de terminal.
 */
@RestController
public class DemoTokenController {

    private final TokenExchangeService tokenExchange;

    public DemoTokenController(TokenExchangeService tokenExchange) {
        this.tokenExchange = tokenExchange;
    }

    @GetMapping("/demo/token")
    public Map<String, Object> mint(@RequestParam String taskId,
                                    @RequestParam(defaultValue = "99182") String customerId) {
        ScopeToken token = tokenExchange.issue(
                "channel:app-mobile",
                CustomerServiceConfiguration.AGENT_NAME,
                "customer:" + customerId,
                Scopes.REFUND_REQUEST,
                null,           // este salto nao move dinheiro: sem teto de valor
                false,
                taskId);

        return Map.of(
                "access_token", token.raw(),
                "scope", token.scope(),
                "audience", token.audience(),
                "on_behalf_of", token.onBehalfOf(),
                "task_id", token.taskId(),
                "expires_at", token.expiresAt().toString());
    }
}
