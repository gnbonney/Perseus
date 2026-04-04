package gnb.perseus.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared runtime support for the current descriptor-based formatted output slice.
 */
public final class TextFormatSupport {
    private TextFormatSupport() {
    }

    public static String format(String formatLiteral, Object[] values) {
        List<String> specs = parseFormatSpecs(formatLiteral);
        if (specs.size() != values.length) {
            throw new IllegalArgumentException("format/argument count mismatch");
        }
        return String.format(toJavaFormat(specs), values);
    }

    private static List<String> parseFormatSpecs(String formatLiteral) {
        String raw = unquote(formatLiteral);
        List<String> specs = new ArrayList<>();
        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            char kind = Character.toUpperCase(token.charAt(0));
            if (kind == 'I' || kind == 'A') {
                Integer.parseInt(token.substring(1));
                specs.add(token.toUpperCase());
            } else if (kind == 'F') {
                int dot = token.indexOf('.');
                Integer.parseInt(token.substring(1, dot));
                Integer.parseInt(token.substring(dot + 1));
                specs.add(token.toUpperCase());
            } else {
                throw new IllegalArgumentException("Unsupported format token: " + token);
            }
        }
        return specs;
    }

    private static String toJavaFormat(List<String> specs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String spec = specs.get(i);
            char kind = spec.charAt(0);
            if (kind == 'F') {
                sb.append('%').append(spec.substring(1).toLowerCase()).append('f');
            } else if (kind == 'I') {
                sb.append('%').append(spec.substring(1)).append('d');
            } else if (kind == 'A') {
                sb.append('%').append(spec.substring(1)).append('s');
            } else {
                throw new IllegalArgumentException("Unsupported format token: " + spec);
            }
        }
        return sb.toString();
    }

    private static String unquote(String literal) {
        if (literal == null || literal.length() < 2 || literal.charAt(0) != '"' || literal.charAt(literal.length() - 1) != '"') {
            return literal;
        }
        return literal.substring(1, literal.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n");
    }

}
