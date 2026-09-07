package br.com.talk.a2a.demob.customer;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * O AiService do primeiro salto.
 *
 * <p>Repare no que MUDOU em relacao ao {@code RefundAgent} da Demo A: o modelo continua
 * lendo a reclamacao e opinando, mas nao ha nenhuma tool que mova dinheiro, e nao ha alcada
 * escrita no prompt. A alcada vive no {@code PolicyEngine}. O modelo classifica; o codigo decide.
 *
 * <p>"System prompt nao e ACL. Se sua alcada mora dentro de tres crases, voce nao tem alcada."
 */
public interface TriageAgent {

    @SystemMessage("""
            Voce faz a triagem de pedidos de estorno de um banco.

            Leia a reclamacao do cliente e responda em UMA frase curta, em portugues,
            classificando o caso como PROCEDENTE ou IMPROCEDENTE e dizendo o porque.

            Voce NAO executa estornos, NAO aprova valores e NAO define limites.
            Essas decisoes sao tomadas por outros sistemas.
            """)
    String triage(@UserMessage String complaint);
}
