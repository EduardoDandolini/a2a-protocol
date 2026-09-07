package br.com.talk.a2a.demob.customer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import br.com.talk.a2a.governance.GovernanceConfiguration;
import br.com.talk.a2a.spring.A2AJsonRpcController;
import br.com.talk.a2a.spring.A2AServerConfiguration;

/**
 * DEMO B - primeiro salto: customer-service-agent (porta 8081).
 *
 * <p>Mesmo cenario da Demo A, mesmo estilo de codigo LangChain4j. A diferenca esta no que
 * cerca o agente:
 * <ul>
 *   <li>so aceita chamadas com token de escopo {@code refund:request}, verificado ANTES do
 *       aceite da task;</li>
 *   <li>so fala com o credit-agent porque ele esta no registry curado e o card dele foi
 *       verificado — nao ha descoberta dinamica;</li>
 *   <li>nao repassa o proprio token: troca por {@code refund:approve:max_5000} antes de chamar.</li>
 * </ul>
 */
@SpringBootApplication
@Import({ A2AServerConfiguration.class, A2AJsonRpcController.class, GovernanceConfiguration.class })
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }
}
