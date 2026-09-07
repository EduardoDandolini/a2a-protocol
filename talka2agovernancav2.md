# Quem autorizou esse agente?
### Governança de sistemas multiagente com A2A e LangChain4j

**Duração:** 20 min
**Formato:** cold open → protocolo → demo ingênua → lacunas → demo governada → fecho

---

## A tese

> O A2A resolve **autenticação**. Ele deliberadamente **não** resolve **autorização**.
> Essa lacuna é sua. E dá para fechar hoje, em Java.

A talk prova isso mostrando **o mesmo sistema duas vezes**: sem governança e com.

---

## Mapa de tempo

| Bloco | Tempo | Acumulado |
|---|---|---|
| 1. Cold open — sem jargão | 1:00 | 1:00 |
| 2. A2A: o que é e por que existe | 2:30 | 3:30 |
| 3. **Demo A** — o agente ingênuo | 3:00 | 6:30 |
| 4. O incidente, agora com vocabulário | 1:30 | 8:00 |
| 5. As três lacunas | 5:30 | 13:30 |
| 6. **Demo B** — aplicação prática governada | 5:00 | 18:30 |
| 7. Checklist e fecho | 1:30 | 20:00 |

**Checkpoints no relógio: 8:00 e 13:30.**
Se passar de 13:30 sem começar a Demo B, corte a lacuna 3 no meio e vá. A Demo B é o produto da talk — ela nunca é sacrificada.

---

## Bloco 1 — Cold open (1 min)

**Slide 1:** preto, uma frase. Sem título, sem seu nome ainda.

**Fala — decore ao pé da letra:**

> Um sistema automatizado devolveu quatro mil e oitocentos reais para um cliente.
> Duas vezes.
>
> Ninguém escreveu essa regra. Nenhum humano aprovou.
> E quando perguntaram quem tinha autorizado aquilo, a resposta honesta era: **ninguém sabe.**
>
> Isso não é um bug. É uma consequência de arquitetura.
> Vou te mostrar como acontece — e como evitar.

**Slide 2:** agora sim título, seu nome, "engenheiro backend, sistema bancário em produção".

> Nota: se o caso não for literal, diga "um cenário que qualquer um de nós vai viver em 2026". Não invente incidente real — o risco no Q&A não compensa.

---

## Bloco 2 — A2A: o que é e por que existe (2:30)

Aqui você paga a dívida narrativa. Sem código ainda, só conceito.

**Slide 3 — o problema que o protocolo resolve.**
Todo time construiu seu agente. Times diferentes, linguagens diferentes, frameworks diferentes. Python com LangGraph aqui, Java com LangChain4j ali. Eles não se falam. Cada integração é um adaptador na mão.

**Slide 4 — MCP vs A2A**, uma linha cada:
- **MCP:** agente ↔ ferramenta. *"Me dá acesso a essa API."*
- **A2A:** agente ↔ agente. *"Resolve isso pra mim, do teu jeito."*

A diferença que importa para o resto da talk:

> Com uma ferramenta, você sabe o que vai acontecer. Com um agente, não.
> Ele é **opaco por design**. E opacidade é exatamente o que governança odeia.

**Slide 5 — os três conceitos que você vai usar o tempo todo:**
- **Agent Card** — JSON público onde o agente declara identidade, skills e como autenticar nele
- **Task** — unidade de trabalho, com ID único e ciclo de vida (`submitted → working → completed/failed`)
- **Transporte** — JSON-RPC 2.0, gRPC ou REST sobre HTTPS. SSE para streaming, webhook para tarefa longa

**Slide 6 — o protocolo é sério em segurança** (esse slide é obrigatório para você não parecer injusto):
- HTTPS obrigatório em produção
- `securitySchemes` no Agent Card, formato OpenAPI 3: OAuth2, OIDC, API key, mTLS
- Assinatura do próprio Agent Card via JWS
- Autenticação exigida em toda requisição

**Fala de transição — importante:**

> Guardem esse slide. Eu vou voltar nele.
> O que **não** está aí é a talk inteira.

---

## Bloco 3 — Demo A: o agente ingênuo (3 min)

**Objetivo duplo:** explicar A2A com código real *e* plantar a armadilha.
O código é a explicação. Não repita em prosa o que o slide de código já diz.

**Stack (mostre o `pom.xml`, 20 segundos):**
```xml
<dependency>
  <groupId>org.a2aproject.sdk</groupId>
  <artifactId>a2a-java-sdk-reference-jsonrpc</artifactId>
  <version>${a2a.version}</version>
</dependency>
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-agentic-a2a</artifactId>
  <version>${lc4j.version}</version>  <!-- beta: diga isso em voz alta -->
</dependency>
```

**Sequência (ensaiada, 3:00 cravados):**

1. **AiService LangChain4j** — `@RegisterAiService`, `@SystemMessage`, uma `@Tool` de estorno.
   *"Isso é uma interface Java. É só isso."*
2. **AgentCardProducer** (CDI) — aqui você **explica Agent Card mostrando**, não contando.
   *"Aqui eu declaro o que meu agente diz que sabe fazer."*
3. **AgentExecutor** — `execute` e `cancel`.
   *"O SDK cuida de protocolo, roteamento e streaming. Eu cuido da lógica."*
4. **A2A Inspector ao vivo** — aponte para o agente, mostre o card sendo descoberto, mande uma mensagem, abra o JSON-RPC cru. Aqui a plateia **vê** a task e o ciclo de vida.
5. **Interop poliglota** (10 segundos, um print): um host agent Python chamando seu agente Java. Prova a promessa do protocolo.

**Fala de fechamento — a armadilha:**

> Levei uma tarde para isso funcionar. E é aí que mora o problema:
> **é fácil demais.**
> Nada nesse fluxo me perguntou quem eu era, o que eu podia fazer, ou até quanto eu podia mover.
> Eu acabei de colocar em produção um agente que executa estorno e aceita ordem de qualquer um que saiba a URL.

---

## Bloco 4 — O incidente (1:30)

Agora a plateia tem vocabulário. A linha de log finalmente dói.

**Slide 7:** três caixas, duas setas.
`Agente de Atendimento → Agente de Crédito → Agente de Pagamento`
Três times. Duas linguagens. Todos falando A2A. Funcionou por seis semanas.

**Slide 8:** a linha de log, sozinha, fonte grande.

```
INFO  task=a7f3c9  agent=payment-agent
      action=refund  amount=R$ 4.812,00
      reason="o agente determinou que a solicitação era apropriada"
```

**Fala:**

> Vocês agora sabem ler isso. Sabem o que é a task. Sabem o que é o agente.
> E sabem que **não tem uma única linha aí que responda a pergunta do título.**
>
> Isso não é log de auditoria. É uma confissão.

---

## Bloco 5 — As três lacunas (5:30)

Volte ao Slide 6 e **risque em vermelho o que falta**. Custo zero, efeito alto.

### Lacuna 1 — Agent Card é descoberta, não confiança (1:30)

O card é um documento que o agente publica **sobre si mesmo**. Ele diz o que o agente *afirma* fazer. Nada nele prova nada.

Pior: o campo `description` do card entra no meta-prompt de quem te chama. Isso é vetor de prompt injection **via documento de descoberta**.

**O que fazer:** a spec prevê assinatura JWS do card sobre forma canonicalizada (JCS). Use — mas note que **a spec não obriga ninguém a verificar**. O default é confiar. Em produção: registry curado interno, allowlist, zero descoberta dinâmica.

**Âncora:** *"Agent Card não é crachá. É cartão de visita — e qualquer um imprime um."*

### Lacuna 2 — Autorização tardia (2:30) ← o pico

**Slide 9 — a timeline:**
```
t0  task aceita           ← autenticação acontece AQUI
t1  LLM raciocina
t2  LLM decide: "vou precisar da tool de estorno"
t3  tool executa          ← autorização acontece AQUI. Ou não acontece.
```

> O A2A autentica muito bem. Ele valida que eu sou quem digo ser, em toda requisição.
> O que ele não define — e está escrito na spec — é um **modelo de autorização**.
>
> E tem um detalhe estrutural: o agente **aceita a task antes de saber quais ferramentas vai precisar.**

O gap entre t0 e t3 é onde vive o incidente do Slide 8. A autorização é tardia, implícita, e inconsistente entre ferramentas. O ecossistema chama isso de **authorization creep**: agentes acumulam poder de agir sobre mais sistemas sem autorização delegada explícita amarrada a cada ação.

**Ligação com o seu domínio — use, é sua vantagem injusta:**

> No banco isso tem nome e tem lei. Não existe "o sistema decidiu".
> Existe **alçada**. Existe quem autorizou, com qual limite, com qual segregação de função. E existe prova.

**Âncora:** *"System prompt não é ACL. Se sua alçada mora dentro de três crases, você não tem alçada."*

### Lacuna 3 — Chamada entre agentes não é transacional (1:30)

Três chamadas de rede, cada uma com um LLM não-determinístico no meio. Isso não é transação. Não tem rollback.

- **Idempotência:** o task ID do A2A é sua chave natural. O estorno duplicado morre aqui.
- **Compensação:** toda ação com efeito colateral precisa de inversa registrada. Saga.
- **Rastreabilidade:** o SDK Java tem add-on de OpenTelemetry e task store em JPA. Correlacione task ID ao trace e persista com retenção de auditoria.

**Transição para a Demo B:**

> Três lacunas. Agora eu vou fechar as três, no mesmo código que vocês viram há dez minutos.

---

## Bloco 6 — Demo B: aplicação prática governada (5 min)

**Regra de ouro:** mesmo cenário, mesmo agente, mesma tela. Só a governança muda.
A plateia precisa reconhecer o código da Demo A.

**Estrutura em três atos:**

### Ato 1 — Autorização na borda (2 min)
Mostre o interceptor/filtro antes do `AgentExecutor`:
- valida assinatura do Agent Card do chamador
- valida token OAuth2 e **extrai o escopo**
- **rejeita a task antes de aceitar** se a skill exigir escopo que o token não tem

Rode uma chamada sem escopo suficiente → **negada em t0, não em t3.**

*"A autorização subiu na timeline. Ela agora acontece antes do LLM abrir a boca."*

### Ato 2 — Escopo atenuado por salto (1:30)
O agente de atendimento **não repassa o próprio token**. Ele troca por um de escopo menor antes de chamar o de crédito.

```
atendimento  → scope: refund:request
crédito      → scope: refund:approve:max_5000
pagamento    → scope: payment:execute:max_5000, single_use
```

*"Cada salto reduz poder. Nunca aumenta. Isso é segregação de função, só que distribuída."*

### Ato 3 — O estorno duplicado (1:30) ← **o clímax da talk**
Rode **exatamente o cenário do cold open**. Dispare o estorno duas vezes.

- Primeira: executa, compensação registrada.
- Segunda: **barrada por idempotência no task ID.**

Mostre o log de saída no terminal:

```
INFO  task=a7f3c9  trace=8b1e...  agent=payment-agent
      action=refund  amount=R$ 4.812,00
      delegated_from=credit-agent   on_behalf_of=customer:99182
      scope=payment:execute:max_5000   authorized_by=policy-engine/v4
      idempotency_key=a7f3c9   compensation=registered

WARN  task=a7f3c9  duplicate suppressed  original=2026-02-11T14:22:07Z
```

**Fala:**

> Mesma ação. Mesmo protocolo. Mesmo LangChain4j.
> A diferença é que agora eu consigo responder a pergunta.

---

## Bloco 7 — Checklist e fecho (1:30)

**Slide de checklist** — um slide, sem animação:

1. Registry curado. Zero descoberta dinâmica em produção.
2. Agent Card assinado **e verificado**.
3. mTLS ou OAuth2 na borda de todo agente.
4. Autorização **antes** de aceitar a task, não dentro da tool.
5. Token delegado com escopo atenuado a cada salto.
6. Idempotência por task ID.
7. Compensação para toda ação com efeito colateral.
8. Limites em código. Nunca no prompt.

**Fecho:**

> O A2A te dá um jeito dos agentes conversarem. Isso é muito, e é bem feito.
> **O que eles têm permissão de fazer quando conversam continua sendo engenharia.**
> Continua sendo nosso trabalho.
>
> Obrigado.

Repositório no último slide. Deixe ele no ar durante o Q&A.

---

## Riscos

- **Demo B é o produto da talk e não existe ainda.** É o maior risco do plano. Se você não conseguir construir os três atos, corte o Ato 2 (escopo atenuado) — vira slide de arquitetura. Atos 1 e 3 são inegociáveis.
- **Grave as duas demos em vídeo.** 90 segundos cada, sem áudio, você narrando ao vivo. Wi-Fi de evento cai.
- **Fixe versões.** O SDK migrou de `io.github.a2asdk` para `org.a2aproject.sdk`; o módulo agentic do LangChain4j está em beta. Revalide o código na semana do evento.
- **Diga você mesmo que o módulo é beta.** Cinco segundos, compra credibilidade para os outros 19 minutos.

## Q&A provável

**"Isso não é service mesh com outro nome?"** — Em parte, e é elogio. mTLS e política já resolvem uma fatia. O que muda é que o consumidor da autorização é não-determinístico: serviço REST chama o que está no código, agente chama o que ele decidir. Sidecar não resolve intenção.

**"Não é cedo para A2A em produção?"** — Provavelmente sim. Mas os pilotos já estão rodando, e a pergunta de governança chega antes da maturidade do stack.

**"E human-in-the-loop?"** — Sua melhor resposta. A spec suporta nativamente via tarefas longas e push notification. Em banco, "acima de X, um humano aprova" é a resposta mais aceita. Ficou de fora por tempo, não por discordância.

**"Como resolveram no banco?"** — Cuidado com NDA. Fale de padrão, não de implementação.
