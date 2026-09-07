package br.com.talk.a2a.demoa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import br.com.talk.a2a.spring.A2AJsonRpcController;
import br.com.talk.a2a.spring.A2AServerConfiguration;

/**
 * DEMO A - "o agente ingenuo".
 *
 * <p>Um agente de estorno A2A-compliant, construido rapido com LangChain4j + A2A Java SDK.
 * Ele funciona. E ninguem verifica QUEM esta chamando, O QUE aquele chamador pode fazer,
 * nem QUANTO ele pode movimentar.
 *
 * <p>A autenticacao (quando existe) acontece na hora de aceitar a task, em t0. A autorizacao,
 * se acontece, acontece la no fundo da execucao da tool, em t3 — ou nunca. Entre t0 e t3 o
 * agente ja decidiu sozinho o que fazer com o dinheiro.
 *
 * <p><b>Nao use este modulo como referencia de producao.</b> Ele existe para reproduzir,
 * de verdade, o incidente da abertura da palestra: o mesmo estorno de R$ 4.812,00 executado
 * duas vezes, com um log que nao responde quem autorizou.
 */
@SpringBootApplication
@Import({ A2AServerConfiguration.class, A2AJsonRpcController.class })
public class NaiveRefundApplication {

    public static void main(String[] args) {
        SpringApplication.run(NaiveRefundApplication.class, args);
    }
}
