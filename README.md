# Quem autorizou esse agente?

Projeto de demonstração da talk **"Quem autorizou esse agente? — Governança de
sistemas multiagente com A2A e LangChain4j"**.

> O A2A resolve **autenticação**. Ele deliberadamente **não** resolve
> **autorização**. Essa lacuna é sua. E dá para fechar hoje, em Java.

A talk prova essa tese mostrando **o mesmo cenário duas vezes**: um estorno
bancário de R$ 4.812,00, primeiro sem governança (Demo A) e depois com
governança (Demo B) — mesmo protocolo (A2A), mesmo framework de agentes
(LangChain4j), mesmo código de negócio. Só a governança muda.

---

## Arquitetura

```
┌─────────────────────────── Demo A — agente ingênuo ───────────────────────────┐
│                                                                                 │
│   curl (sem token) ──► demo-a-naive-agent :8080                               │
│                          AiService (LangChain4j) + @Tool de estorno           │
│                          nenhuma checagem de quem chama, do que pode fazer,   │
│                          ou de quanto pode mover                              │
│                                                                                 │
│   Resultado: dispare a MESMA task duas vezes → estorno executa DUAS vezes     │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────── Demo B — cadeia governada ─────────────────────────┐
│                                                                                 │
│   curl (token OAuth2)                                                         │
│        │  scope: refund:request                                              │
│        ▼                                                                      │
│   customer-service-agent :8081  ──► troca o token (nunca repassa o próprio)  │
│        │  scope: refund:approve:max_5000                                     │
│        ▼                                                                      │
│   credit-agent :8082  ──► avalia, confere valor, troca o token de novo       │
│        │  scope: payment:execute:max_5000, single_use                       │
│        ▼                                                                      │
│   payment-agent :8083  ──► autoriza NA BORDA, checa idempotência,           │
│                             executa, registra compensação                    │
│                                                                                 │
│   Resultado: dispare a MESMA task duas vezes → executa uma vez, a segunda    │
│              é suprimida por idempotência (mesmo contextId)                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

Todos os agentes falam A2A de verdade (JSON-RPC 2.0 sobre HTTP, Agent Card em
`/.well-known/agent-card.json`, ciclo de vida de task `submitted → working →
completed/failed`) — não é uma simulação em processo único.

### Módulos

| Módulo | O quê | Porta |
|---|---|---|
| `governance-common` | Motor de política, atenuação de escopo, interceptor de autorização, idempotência, compensação, verificação JWS/JCS do Agent Card, registry curado | — |
| `a2a-spring-support` | Hospedagem do endpoint JSON-RPC do A2A Java SDK sobre Spring MVC (o SDK só publica exemplos de referência para Quarkus/CDI) | — |
| `demo-a-naive-agent` | Agente de estorno sem nenhuma checagem de autorização | 8080 |
| `demo-b-governed/customer-service-agent` | 1º salto: recebe o pedido, troca o token, aciona o crédito | 8081 |
| `demo-b-governed/credit-agent` | 2º salto: avalia e aprova dentro da alçada, aciona o pagamento | 8082 |
| `demo-b-governed/payment-agent` | 3º salto: autoriza na borda, idempotência, executa, compensação | 8083 |

---

## Pré-requisitos

- **Java 21** e **Maven**
- **Ollama** rodando localmente com um modelo pequeno baixado, por exemplo:
  ```
  ollama pull llama3.2
  ollama serve
  ```
  Se o Ollama não estiver acessível, os executores caem num caminho
  determinístico (a análise do LLM vira um texto fixo, "indisponível") — a
  demonstração de governança **não depende** do modelo responder; o LLM é
  só o "cérebro" opinativo, nunca o portão de autorização.
- `curl` e `python3` (usados pelos scripts de demo em `scripts/`)

Configuração do Ollama (`ollama.base-url`, `ollama.model-id`,
`ollama.timeout-seconds`) fica em cada `application.properties`, com
`http://localhost:11434` e `llama3.2` como padrão.

---

## Build

```bash
mvn -DskipTests install
```

Compila os seis módulos e empacota cada agente como um jar executável do
Spring Boot. Para rodar os testes (16 testes em `governance-common`: escopo,
atenuação, single-use, idempotência, compensação, canonicalização JCS, card
adulterado, registry):

```bash
mvn install
```

---

## Rodando a Demo A — o agente ingênuo

```bash
cd demo-a-naive-agent
mvn spring-boot:run
```

Em outro terminal:

```bash
./scripts/demo-a-double-refund.sh
```

O script dispara a mesma operação de negócio duas vezes, **sem nenhum
header `Authorization`**. Nada impede: o agente aceita a task, o LLM
raciocina, a tool de estorno executa. Duas vezes. Olhe o log do agente —
a única justificativa registrada é:

```
reason="o agente determinou que a solicitação era apropriada"
```

Isso não responde a pergunta do título.

---

## Rodando a Demo B — a cadeia governada

Suba os três agentes (em terminais separados, ou em background):

```bash
cd demo-b-governed/payment-agent          && mvn spring-boot:run &
cd demo-b-governed/credit-agent           && mvn spring-boot:run &
cd demo-b-governed/customer-service-agent && mvn spring-boot:run &
```

Aguarde os três subirem (Tomcat nas portas 8081/8082/8083) e rode:

```bash
./scripts/demo-b-governed.sh
```

O script faz, na ordem:

1. **Sem token** → `401` na borda. A task nem chega a existir.
2. **Com token de escopo `refund:request`**, amarrado à operação
   `a7f3c9` → a cadeia inteira executa: atendimento → crédito → pagamento.
   Cada salto troca o token por um mais estreito antes de chamar o
   próximo (nunca repassa o próprio). O estorno é executado e uma
   compensação é registrada.
3. **A MESMA operação, de novo** (mesmo `contextId`, token novo) →
   suprimida por idempotência.
4. Consulta a auditoria do `payment-agent` (`GET /demo/audit`): um único
   lançamento, uma única compensação, apesar de dois envios.

No log do `payment-agent` você vê, nesta ordem:

```
INFO  task=a7f3c9  trace=8b1e...  agent=payment-agent action=refund \
      amount=R$ 4.812,00 delegated_from=credit-agent on_behalf_of=customer:99182 \
      scope=payment:execute:max_5000 authorized_by=policy-engine/v4 \
      idempotency_key=a7f3c9 compensation=registered
WARN  task=a7f3c9  duplicate suppressed  original=<timestamp>
```

Agora dá para responder: quem autorizou esse agente?

### Detalhe de protocolo: a chave de idempotência não é o task ID local

Em A2A 1.0 o **task ID é gerado pelo servidor** — um cliente não pode
escolhê-lo. Cada salto da cadeia tem, portanto, o seu próprio task ID.
O identificador que o cliente fixa e que atravessa os três saltos é o
**`contextId`**: é ele que ancora a idempotência do `payment-agent`, o
vínculo do token delegado (`ScopeToken.taskId`), a chave do registro de
compensação, e o campo `task=` da linha de log governada. Ver
`ChainCorrelation.java`.

---

## Onde cada item do checklist final está implementado

| # | Item do checklist | Onde |
|---|---|---|
| 1 | Registry curado. Zero descoberta dinâmica em produção. | `AgentRegistry` — cada agente da Demo B só fala com URLs pré-cadastradas (`CreditConfiguration`, `CustomerServiceConfiguration`, `PaymentConfiguration`) |
| 2 | Agent Card assinado e verificado. | `AgentCardSigner` (JWS sobre a forma canonicalizada JCS, `JsonCanonicalizer`) + `AgentCardVerifier`, chamado pelo `AgentRegistry` antes de confiar em qualquer card |
| 3 | mTLS ou OAuth2 na borda de todo agente. | `AuthorizationInterceptor` (Spring `HandlerInterceptor`) — bearer token OAuth2-like em toda requisição, antes de qualquer coisa do A2A SDK ver o payload |
| 4 | Autorização antes de aceitar a task, não dentro da tool. | Duas barreiras: `AuthorizationInterceptor` (borda HTTP) e `ScopeTokenTaskAuthorizationProvider` (hook nativo do SDK, `TaskAuthorizationProvider`, chamado pelo `DefaultRequestHandler` antes do aceite) |
| 5 | Token delegado com escopo atenuado a cada salto. | `TokenExchangeService.attenuate()` — cada `*Executor` troca o token recebido por um mais estreito antes de chamar o próximo agente; nunca repassa o próprio |
| 6 | Idempotência por task ID (na prática, por `contextId` — ver acima). | `IdempotencyStore`, checado em `PaymentExecutor` |
| 7 | Compensação para toda ação com efeito colateral. | `CompensationLog`, registrado em `PaymentExecutor` a cada estorno executado |
| 8 | Limites em código. Nunca no prompt. | `PolicyEngine` — teto de valor e escopo exigido por skill são código Java, checados três vezes (borda, `TaskAuthorizationProvider`, executor), nunca decididos pelo LLM |

> "System prompt não é ACL. Se sua alçada mora dentro de três crases, você
> não tem alçada."
>
> "Agent Card não é crachá. É cartão de visita — e qualquer um imprime um."

---

## O que é simplificado nesta demo (e o que trocar em produção)

- **Token exchange** (`HmacTokenExchangeService`): um segredo HMAC
  compartilhado entre os três agentes, no lugar de um IdP real. Trocar por
  OAuth 2.0 Token Exchange (RFC 8693) é substituir esta classe, não o
  resto — a semântica (escopo único por token, teto numérico, vínculo com
  a operação, TTL curto, atenuação monotônica) é a que valeria em
  produção.
- **Assinatura do Agent Card** (`AgentCardSigner`/`AgentCardVerifier`): um
  segredo HMAC único no lugar de um par de chaves assimétricas com
  rotação e um key server real.
- **Task store, idempotency store e compensation log**: em memória
  (`InMemoryTaskStore`, `InMemoryIdempotencyStore`, `InMemoryCompensationLog`).
  A interface de cada um permite trocar por uma implementação em JPA (ou
  outro armazenamento durável) sem tocar no agente — é exatamente o que o
  SDK oficial oferece em `a2a-java-sdk-extras-task-store-database-jpa`.
- **Rastreabilidade**: o SDK tem um add-on de OpenTelemetry
  (`a2a-java-sdk-opentelemetry-server`) para correlacionar `task`/`trace`
  e persistir com retenção de auditoria; fora do escopo desta demo.
- **`/demo/token`** (`DemoTokenController`, só no `customer-service-agent`):
  fica no lugar do IdP corporativo, só para a demo rodar com um `curl`.
  Não existiria em produção — nenhum agente emite credencial para si mesmo.

---

## Sobre o LangChain4j

O módulo `dev.langchain4j:langchain4j-agentic-a2a` (usado no roteiro da
talk para a integração declarativa AiService ↔ A2A) está declarado no
`dependencyManagement` do `pom.xml` raiz e é **beta** — dito em voz alta
aqui, como a talk recomenda. Nesta implementação, a integração com o
protocolo A2A é feita diretamente pelo SDK oficial
(`org.a2aproject.sdk:*`), com o LangChain4j (`AiServices`) usado só para
a parte de raciocínio (`RefundAgent`, `TriageAgent`, `CreditAnalystAgent`),
mantendo autorização, escopo e limites inteiramente em código — nunca
dentro do prompt.

---

## Repositório

Este repositório é o material de apoio da talk **"Quem autorizou esse
agente? — Governança de sistemas multiagente com A2A e LangChain4j"**.
