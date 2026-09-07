package br.com.talk.a2a.spring;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Anuncia ao SDK que o transporte JSON-RPC esta disponivel nesta aplicacao.
 *
 * <p>O {@code AgentCardValidator} do SDK descobre os transportes via {@link java.util.ServiceLoader}
 * e recusa um Agent Card que declare uma interface para um transporte que ninguem implementa
 * ("AgentCard specifies transport interfaces for unavailable transports"). Na implementacao de
 * referencia Quarkus quem cumpre esse papel e a
 * {@code QuarkusJSONRPCTransportMetadata}; aqui, quem serve o JSON-RPC e o
 * {@link A2AJsonRpcController}, entao e esta classe.
 *
 * <p>Registrada em {@code META-INF/services/org.a2aproject.sdk.server.TransportMetadata}.
 */
public class SpringJsonRpcTransportMetadata implements TransportMetadata {

    @Override
    public String getTransportProtocol() {
        return TransportProtocol.JSONRPC.asString();
    }
}
