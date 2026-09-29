package legalvideo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.value();
        parser.space();
        if (parser.index != text.length()) throw new IllegalArgumentException("Unexpected JSON tail");
        return value;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new IllegalArgumentException("Expected a JSON object");
        return (Map<String, Object>) value;
    }

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String) return quote((String) value);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder out = new StringBuilder("{");
            boolean comma = false;
            for (Object entryObject : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                if (comma) out.append(',');
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(stringify(entry.getValue()));
                comma = true;
            }
            return out.append('}').toString();
        }
        if (value instanceof Iterable) {
            StringBuilder out = new StringBuilder("[");
            boolean comma = false;
            for (Object item : (Iterable<?>) value) {
                if (comma) out.append(',');
                out.append(stringify(item));
                comma = true;
            }
            return out.append(']').toString();
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass());
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('\"').toString();
    }

    private static final class Parser {
        private final String text;
        private int index;

        Parser(String text) { this.text = text; }
        void space() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
        char take() { return text.charAt(index++); }

        Object value() {
            space();
            if (index >= text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = text.charAt(index);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '\"') return string();
            if (c == 't') { literal("true"); return true; }
            if (c == 'f') { literal("false"); return false; }
            if (c == 'n') { literal("null"); return null; }
            return number();
        }

        Map<String, Object> object() {
            take();
            Map<String, Object> result = new LinkedHashMap<>();
            space();
            if (text.charAt(index) == '}') { index++; return result; }
            while (true) {
                space();
                String key = string();
                space();
                if (take() != ':') throw new IllegalArgumentException("Expected colon");
                result.put(key, value());
                space();
                char next = take();
                if (next == '}') return result;
                if (next != ',') throw new IllegalArgumentException("Expected comma");
            }
        }

        List<Object> array() {
            take();
            List<Object> result = new ArrayList<>();
            space();
            if (text.charAt(index) == ']') { index++; return result; }
            while (true) {
                result.add(value());
                space();
                char next = take();
                if (next == ']') return result;
                if (next != ',') throw new IllegalArgumentException("Expected comma");
            }
        }

        String string() {
            if (take() != '\"') throw new IllegalArgumentException("Expected string");
            StringBuilder result = new StringBuilder();
            while (index < text.length()) {
                char c = take();
                if (c == '\"') return result.toString();
                if (c != '\\') { result.append(c); continue; }
                char escaped = take();
                switch (escaped) {
                    case '\"': result.append('\"'); break;
                    case '\\': result.append('\\'); break;
                    case '/': result.append('/'); break;
                    case 'b': result.append('\b'); break;
                    case 'f': result.append('\f'); break;
                    case 'n': result.append('\n'); break;
                    case 'r': result.append('\r'); break;
                    case 't': result.append('\t'); break;
                    case 'u': result.append((char) Integer.parseInt(text.substring(index, index + 4), 16)); index += 4; break;
                    default: throw new IllegalArgumentException("Bad escape");
                }
            }
            throw new IllegalArgumentException("Unclosed string");
        }

        Number number() {
            int start = index;
            while (index < text.length() && "-+0123456789.eE".indexOf(text.charAt(index)) >= 0) index++;
            String token = text.substring(start, index);
            return token.contains(".") || token.contains("e") || token.contains("E")
                ? Double.parseDouble(token) : Long.parseLong(token);
        }

        void literal(String expected) {
            if (!text.startsWith(expected, index)) throw new IllegalArgumentException("Bad literal");
            index += expected.length();
        }
    }
}
