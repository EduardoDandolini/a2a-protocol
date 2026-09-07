package br.com.talk.a2a.demob.payment;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.talk.a2a.governance.CompensationLog;

/**
 * Endpoint de auditoria da demo: mostra o que foi lancado e quais compensacoes existem.
 *
 * <p>Serve para provar no palco, sem ler log, que a segunda chamada NAO gerou um segundo
 * lancamento — e que cada lancamento tem uma acao inversa registrada.
 *
 * <p>Fica fora do interceptor de autorizacao (que so cobre {@code /}) porque e um recurso de
 * demonstracao. Num sistema real este endpoint seria protegido como qualquer outro.
 */
@RestController
public class AuditController {

    private final PaymentLedger ledger;
    private final CompensationLog compensationLog;

    public AuditController(PaymentLedger ledger, CompensationLog compensationLog) {
        this.ledger = ledger;
        this.compensationLog = compensationLog;
    }

    @GetMapping("/demo/audit")
    public Map<String, Object> audit() {
        List<PaymentLedger.Entry> entries = ledger.all();
        return Map.of(
                "lancamentos", entries,
                "quantidade_lancamentos", entries.size(),
                "total_movimentado", ledger.total().toPlainString(),
                "compensacoes", compensationLog.all());
    }
}
