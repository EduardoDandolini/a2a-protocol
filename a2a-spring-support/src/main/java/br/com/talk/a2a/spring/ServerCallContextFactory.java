package br.com.talk.a2a.spring;

import jakarta.servlet.http.HttpServletRequest;

import org.a2aproject.sdk.server.ServerCallContext;

/**
 * Ponto de extensao para construir o {@link ServerCallContext} a partir da requisicao HTTP.
 *
 * <p>Equivale ao {@code CallContextFactory} que a implementacao de referencia Quarkus do SDK
 * expoe sobre o {@code RoutingContext} do Vert.x — aqui sobre o {@link HttpServletRequest}
 * do Spring MVC.
 *
 * <p>E por aqui que a Demo B injeta o token delegado ja verificado no estado da chamada,
 * de forma que o {@code TaskAuthorizationProvider} (que roda ANTES do aceite da task) e o
 * {@code AgentExecutor} enxerguem quem esta chamando, em nome de quem, e com qual escopo.
 */
@FunctionalInterface
public interface ServerCallContextFactory {

    ServerCallContext build(HttpServletRequest request);
}
