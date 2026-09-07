package br.com.talk.a2a.governance;

import java.math.BigDecimal;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

/**
 * O payload de negocio que atravessa os tres saltos da cadeia da Demo B.
 *
 * <p>Ele viaja como texto dentro da {@code Message} A2A. E deliberadamente estruturado:
 * valor e {@link BigDecimal}, nao um numero que o modelo escreveu numa frase. O LLM opina
 * sobre a procedencia do estorno; ele nao decide o valor nem a alcada.
 *
 * <p>"Limites em codigo. Nunca no prompt."
 */
public record RefundRequest(String customerId, BigDecimal amount, String reason) {

    private static final Gson GSON = new Gson();

    public static String toJson(RefundRequest request) {
        return GSON.toJson(request);
    }

    public static RefundRequest fromJson(String json) {
        try {
            RefundRequest request = GSON.fromJson(json, RefundRequest.class);
            if (request == null || request.customerId() == null || request.amount() == null) {
                throw new IllegalArgumentException("Payload de estorno incompleto: " + json);
            }
            return request;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Payload de estorno invalido: " + json, e);
        }
    }

    /** O principal de negocio em nome de quem a cadeia age. */
    public String principal() {
        return "customer:" + customerId;
    }
}
