package br.com.talk.a2a.governance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/**
 * Canonicalizacao JSON no espirito da RFC 8785 (JCS).
 *
 * <p>Por que isto existe: a assinatura JWS de um Agent Card e feita sobre a forma
 * CANONICALIZADA do JSON, nao sobre os bytes que voce recebeu. Sem isso, reordenar chaves,
 * mexer no espacamento ou reserializar o card — coisas que qualquer proxy, gateway ou
 * biblioteca faz sem avisar — quebraria a assinatura, e todo mundo acabaria desligando
 * a verificacao "porque dava falso positivo".
 *
 * <p>Regras aplicadas: chaves de objeto ordenadas por code point UTF-16, sem espacos em
 * branco, arrays preservam a ordem, strings com escaping minimo, numeros no formato mais
 * curto que preserva o valor.
 *
 * <p><b>Simplificacao de demo:</b> a serializacao numerica da RFC 8785 (ECMAScript
 * {@code Number::toString}) e implementada aqui de forma pragmatica, suficiente para os
 * valores que aparecem num Agent Card (inteiros pequenos e booleanos). Um sistema de
 * producao deve usar uma implementacao JCS testada contra os vetores da RFC.
 */
public final class JsonCanonicalizer {

    private JsonCanonicalizer() {
    }

    /** Canonicaliza um documento JSON recebido como texto. */
    public static String canonicalize(String json) {
        return write(JsonParser.parseString(json));
    }

    /**
     * Canonicaliza um documento JSON removendo antes um membro do objeto raiz.
     *
     * <p>E o que a assinatura destacada de um Agent Card exige: a assinatura e calculada sobre
     * o card SEM o campo {@code signatures}, senao o proprio ato de assinar mudaria o que
     * esta sendo assinado.
     *
     * <p>Fazer isso sobre o TEXTO recebido (e nao sobre um objeto reconstruido) e o ponto
     * inteiro do JCS: qualquer normalizacao que a biblioteca de desserializacao aplique —
     * defaults preenchidos, campos vazios omitidos, uma passagem por protobuf, como faz o
     * resolver de cards do proprio SDK — quebraria a assinatura sem que nada estivesse errado.
     */
    public static String canonicalizeWithout(String json, String rootMember) {
        JsonElement element = JsonParser.parseString(json);
        if (element.isJsonObject()) {
            element.getAsJsonObject().remove(rootMember);
        }
        return write(element);
    }

    private static String write(JsonElement element) {
        StringBuilder out = new StringBuilder();
        write(element, out);
        return out.toString();
    }

    private static void write(JsonElement element, StringBuilder out) {
        if (element == null || element.isJsonNull()) {
            out.append("null");
        } else if (element.isJsonObject()) {
            writeObject(element.getAsJsonObject(), out);
        } else if (element.isJsonArray()) {
            writeArray(element.getAsJsonArray(), out);
        } else {
            writePrimitive(element.getAsJsonPrimitive(), out);
        }
    }

    private static void writeObject(JsonObject object, StringBuilder out) {
        List<Map.Entry<String, JsonElement>> members = new ArrayList<>(object.entrySet());
        // RFC 8785: ordenacao lexicografica sobre as unidades de codigo UTF-16 da chave.
        members.sort(Comparator.comparing(Map.Entry::getKey));

        out.append('{');
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            writeString(members.get(i).getKey(), out);
            out.append(':');
            write(members.get(i).getValue(), out);
        }
        out.append('}');
    }

    private static void writeArray(JsonArray array, StringBuilder out) {
        out.append('[');
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            write(array.get(i), out);
        }
        out.append(']');
    }

    private static void writePrimitive(JsonPrimitive primitive, StringBuilder out) {
        if (primitive.isBoolean()) {
            out.append(primitive.getAsBoolean());
        } else if (primitive.isNumber()) {
            out.append(number(primitive.getAsBigDecimal()));
        } else {
            writeString(primitive.getAsString(), out);
        }
    }

    private static String number(BigDecimal value) {
        if (value.stripTrailingZeros().scale() <= 0) {
            return value.stripTrailingZeros().toBigInteger().toString();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
