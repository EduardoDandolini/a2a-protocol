#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# DEMO B - a mesma coisa, governada.
#
# Dispara a MESMA task duas vezes contra a cadeia governada:
#   customer-service-agent (8081) -> credit-agent (8082) -> payment-agent (8083)
#
# Resultado esperado:
#   1a vez  -> executa, registra compensacao, e o log responde quem autorizou
#   2a vez  -> WARN duplicate suppressed (idempotencia por task ID)
#
# Pre-requisito: os tres agentes no ar (veja o README).
# ---------------------------------------------------------------------------
set -euo pipefail

CS_URL="${CS_URL:-http://localhost:8081/}"
PAY_URL="${PAY_URL:-http://localhost:8083}"

# O contextId E a identidade da operacao de negocio: e a chave de idempotencia,
# e e a ele que o token delegado fica amarrado, salto a salto.
CONTEXT_ID="${CONTEXT_ID:-a7f3c9}"
TRACE_ID="${TRACE_ID:-8b1e4f2a9c7d0e13}"
CUSTOMER="${CUSTOMER:-99182}"

need() { command -v "$1" >/dev/null || { echo "Precisa de '$1' no PATH."; exit 1; }; }
need curl
need python3

mint_token() {
  # Fica no lugar do IdP. Emite o token de BORDA: escopo refund:request,
  # audiencia customer-service-agent, amarrado a ESTA task, valido por 60s.
  curl -sS "http://localhost:8081/demo/token?taskId=${CONTEXT_ID}&customerId=${CUSTOMER}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])'
}

send() {
  local n="$1" token="$2"
  echo
  echo "=== Envio #${n} - contextId ${CONTEXT_ID} (o MESMO das duas vezes) ==="
  curl -sS -X POST "$CS_URL" \
    -H 'Content-Type: application/json' -H 'A2A-Version: 1.0' \
    -H "Authorization: Bearer ${token}" \
    -H "X-Trace-Id: ${TRACE_ID}" \
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
        { "text": "{\"customerId\":\"${CUSTOMER}\",\"amount\":4812.00,\"reason\":\"cobranca duplicada no cartao\"}" }
      ]
    }
  }
}
JSON
)"
  echo
}

echo "-------------------------------------------------------------------"
echo "1) Sem token nenhum - a task nem chega a existir (401 na borda)"
echo "-------------------------------------------------------------------"
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -X POST "$CS_URL" \
  -H 'Content-Type: application/json' -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","id":"x","method":"SendMessage","params":{"message":{"role":"ROLE_USER","messageId":"m","parts":[{"text":"{}"}]}}}'

echo
echo "-------------------------------------------------------------------"
echo "2) Com token de escopo refund:request, amarrado a operacao ${CONTEXT_ID}"
echo "-------------------------------------------------------------------"
TOKEN="$(mint_token)"
send 1 "$TOKEN"

echo
echo "-------------------------------------------------------------------"
echo "3) A MESMA operacao, de novo (token novo, operacao de negocio identica)"
echo "-------------------------------------------------------------------"
TOKEN2="$(mint_token)"
send 2 "$TOKEN2"

echo
echo "-------------------------------------------------------------------"
echo "Auditoria do payment-agent: UM lancamento, UMA compensacao registrada"
echo "-------------------------------------------------------------------"
curl -sS "${PAY_URL}/demo/audit" | python3 -m json.tool

cat <<'FIM'

-------------------------------------------------------------------
No log do payment-agent voce deve ver, na ordem:

INFO  task=a7f3c9  trace=8b1e...  agent=payment-agent action=refund \
      amount=R$ 4.812,00 delegated_from=credit-agent on_behalf_of=customer:99182 \
      scope=payment:execute:max_5000 authorized_by=policy-engine/v4 \
      idempotency_key=a7f3c9 compensation=registered
WARN  task=a7f3c9  duplicate suppressed  original=<timestamp>

Agora da para responder: quem autorizou esse agente?
-------------------------------------------------------------------
FIM
