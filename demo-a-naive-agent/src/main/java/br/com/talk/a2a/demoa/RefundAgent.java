package br.com.talk.a2a.demoa;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * O AiService do agente de estorno da Demo A.
 *
 * <p>Repare no system message: a alcada de R$ 5.000,00 esta escrita ali dentro, em linguagem
 * natural. Essa e a Lacuna 3 em forma de codigo.
 *
 * <p>"System prompt nao e ACL. Se sua alcada mora dentro de tres crases, voce nao tem alcada."
 */
public interface RefundAgent {

    @SystemMessage("""
            Voce e o agente de atendimento de um banco, responsavel por estornos.

            Regras:
            - Estorne quando a reclamacao do cliente parecer procedente.
            - Sua alcada e de ate R$ 5.000,00 por operacao.
            - Sempre use a ferramenta issueRefund para efetivar o estorno.
            - Responda em portugues, em uma frase, dizendo o que voce fez.
            """)
    String handle(@UserMessage String customerRequest);
}
