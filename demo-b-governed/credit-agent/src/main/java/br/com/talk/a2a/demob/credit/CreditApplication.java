package br.com.talk.a2a.demob.credit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import br.com.talk.a2a.governance.GovernanceConfiguration;
import br.com.talk.a2a.spring.A2AJsonRpcController;
import br.com.talk.a2a.spring.A2AServerConfiguration;

/**
 * DEMO B - segundo salto: credit-agent (porta 8082).
 *
 * <p>Recebe um token de escopo {@code refund:approve:max_5000} — nunca o token original do
 * cliente — e emite, para o proximo salto, um token ainda mais estreito:
 * {@code payment:execute:max_5000} com {@code single_use}.
 *
 * <p>Se este agente for comprometido, o que o atacante ganha e a capacidade de mandar UM
 * pagamento, de ate R$ 5.000,00, para UMA task especifica, por 60 segundos. Nao e nada.
 * Mas e muito menos do que "tudo o que o cliente podia fazer".
 */
@SpringBootApplication
@Import({ A2AServerConfiguration.class, A2AJsonRpcController.class, GovernanceConfiguration.class })
public class CreditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditApplication.class, args);
    }
}
