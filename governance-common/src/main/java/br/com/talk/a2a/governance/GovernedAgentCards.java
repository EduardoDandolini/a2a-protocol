package br.com.talk.a2a.governance;

import java.util.List;
import java.util.Map;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.HTTPAuthSecurityScheme;
import org.a2aproject.sdk.spec.SecurityRequirement;
import org.a2aproject.sdk.spec.SecurityScheme;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Monta os Agent Cards da Demo B: com esquema de seguranca declarado, escopo exigido por
 * skill, e assinatura JWS sobre a forma canonicalizada.
 *
 * <p>Compare com o card da Demo A, que nao declara {@code securitySchemes}, nao declara
 * escopo por skill e nao traz assinatura.
 *
 * <p>Ainda assim, vale repetir: nada disso torna o card uma prova de identidade.
 * "Agent Card nao e cracha. E cartao de visita — e qualquer um imprime um."
 * O card assinado so vale porque o consumidor (1) so aceita URLs do registry curado e
 * (2) verifica a assinatura antes de chamar.
 */
public final class GovernedAgentCards {

    public static final String BEARER_SCHEME_NAME = "delegatedBearer";

    private GovernedAgentCards() {
    }

    /**
     * @param name           nome logico do agente, ex. {@code payment-agent}
     * @param description    descricao humana
     * @param url            URL publica deste agente
     * @param skillId        id da skill exposta
     * @param skillName      nome da skill
     * @param skillDesc      descricao da skill
     * @param requiredScope  o escopo que a skill exige — declarado no card, checado em codigo
     * @param signer         assinador; o card sai daqui ja com a assinatura JWS anexada
     */
    public static AgentCard build(String name,
                                  String description,
                                  String url,
                                  String skillId,
                                  String skillName,
                                  String skillDesc,
                                  String requiredScope,
                                  AgentCardSigner signer) {

        SecurityScheme bearer = HTTPAuthSecurityScheme.builder()
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Token delegado com escopo atenuado, emitido pelo token exchange "
                        + "e amarrado a uma unica task A2A.")
                .build();

        AgentSkill skill = AgentSkill.builder()
                .id(skillId)
                .name(skillName)
                .description(skillDesc)
                .tags(List.of("banking", "refund", "governed"))
                .securityRequirements(List.of(SecurityRequirement.builder()
                        .scheme(BEARER_SCHEME_NAME, List.of(requiredScope))
                        .build()))
                .build();

        AgentCard card = AgentCard.builder()
                .name(name)
                .description(description)
                .version("1.0.0")
                .provider(new AgentProvider("Banco Demo", "https://example.invalid"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text/plain", "application/json"))
                .defaultOutputModes(List.of("text/plain", "application/json"))
                .skills(List.of(skill))
                .securitySchemes(Map.of(BEARER_SCHEME_NAME, bearer))
                .securityRequirements(List.of(SecurityRequirement.builder()
                        .scheme(BEARER_SCHEME_NAME, List.of(requiredScope))
                        .build()))
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(),
                        url,
                        null,
                        AgentInterface.CURRENT_PROTOCOL_VERSION)))
                .url(url)
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();

        return signer.sign(card);
    }
}
