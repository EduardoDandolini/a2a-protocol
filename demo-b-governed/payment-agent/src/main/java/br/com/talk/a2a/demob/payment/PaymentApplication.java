package br.com.talk.a2a.demob.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import br.com.talk.a2a.governance.GovernanceConfiguration;
import br.com.talk.a2a.spring.A2AJsonRpcController;
import br.com.talk.a2a.spring.A2AServerConfiguration;

/**
 * DEMO B - terceiro salto: payment-agent (porta 8083). O agente que move dinheiro.
 *
 * <p>E aqui que o climax da Demo B acontece. Dispare a MESMA task duas vezes:
 * <ul>
 *   <li>na primeira, o estorno e executado e uma compensacao e registrada;</li>
 *   <li>na segunda, a idempotencia por task ID bloqueia, e o log diz
 *       {@code duplicate suppressed}.</li>
 * </ul>
 *
 * <p>Note que o token de uso unico ja teria barrado o replay do MESMO token. A idempotencia
 * por task ID e uma camada diferente: ela barra tambem o retry legitimo, o reenvio do
 * orquestrador, e o "clicou duas vezes" — casos em que o token e novo e valido, mas a
 * operacao de negocio e a mesma.
 */
@SpringBootApplication
@Import({ A2AServerConfiguration.class, A2AJsonRpcController.class, GovernanceConfiguration.class })
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
