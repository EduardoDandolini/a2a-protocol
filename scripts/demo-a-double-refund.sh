#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# DEMO A - reproduz o incidente da abertura da palestra.
#
# Dispara a MESMA operacao de negocio duas vezes contra o agente ingenuo (porta 8080).
# Resultado esperado: o estorno de R$ 4.812,00 sai DUAS vezes, e o log so diz que
# "o agente determinou que a solicitacao era apropriada".
#
# Nota de protocolo: em A2A 1.0 o task ID e gerado pelo SERVIDOR - um cliente nao pode
# escolhe-lo (mandar um id inexistente da TaskNotFound). O identificador que o cliente fixa,
# e que identifica a operacao de negocio, e o contextId. E ele que a Demo B usa como chave
# de idempotencia; a Demo A simplesmente o ignora.
#
# Pre-requisito: cd demo-a-naive-agent && mvn spring-boot:run
# ---------------------------------------------------------------------------
set -euo pipefail

AGENT_URL="${AGENT_URL:-http://localhost:8080/}"
CONTEXT_ID="${CONTEXT_ID:-a7f3c9}"

send() {
  local n="$1"
  echo
  echo "=== Envio #${n} - contextId ${CONTEXT_ID} (o MESMO das duas vezes) ==="
  curl -sS -X POST "$AGENT_URL" \
    -H 'Content-Type: application/json' \
    -H 'A2A-Version: 1.0' \
    -d "$(cat <<JSON
{
  "jsonrpc": "2.0",
  "id": "req-${n}",
  "method": "SendMessage",
  "params": {
    "message": {
      "role": "ROLE_USER",
      "messageId": "msg-${n}",

      "contextId": "${CONTEXT_ID}",
      "parts": [
        { "text": "Fui cobrado duas vezes. Estornar R$ 4.812,00 do cliente 99182." }
      ]
    }
  }
}
JSON
)"
  echo
}

echo "Nenhum header Authorization. Nenhum token. Nenhum escopo."
echo "O agente da Demo A nao pergunta quem esta chamando."

send 1
send 2

echo
echo "-------------------------------------------------------------------"
echo "Olhe o log do agente: DOIS estornos, mesma operacao, mesmo valor."
echo "E a unica justificativa registrada e:"
echo '  reason="o agente determinou que a solicitacao era apropriada"'
echo
echo "Isso nao responde: quem autorizou esse agente?"
echo "-------------------------------------------------------------------"
