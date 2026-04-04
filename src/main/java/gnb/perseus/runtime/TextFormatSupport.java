package gnb.perseus.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shared runtime support for descriptor-based formatted output.
 */
public final class TextFormatSupport {
    private TextFormatSupport() {
    }

    public static String format(String formatLiteral, Object[] values) {
        List<String> specs = parseFormatSpecs(formatLiteral);
        int consumedSpecs = countValueSpecs(specs);
        if (consumedSpecs != values.length) {
            throw new IllegalArgumentException("format/argument count mismatch");
        }

        StringBuilder out = new StringBuilder();
        int valueIndex = 0;
        String previousSpec = null;
        for (String spec : specs) {
            if (needsImplicitSeparator(previousSpec, spec)) {
                out.append(' ');
            }
            if ("/".equals(spec)) {
                out.append('\n');
            } else if (spec.endsWith("X")) {
                int spaces = Integer.parseInt(spec.substring(0, spec.length() - 1));
                out.append(" ".repeat(spaces));
            } else {
                out.append(formatValue(spec, values[valueIndex++]));
            }
            previousSpec = spec;
        }
        return out.toString();
    }

    private static boolean needsImplicitSeparator(String previousSpec, String currentSpec) {
        if (previousSpec == null) {
            return false;
        }
        return isValueSpec(previousSpec) && isValueSpec(currentSpec);
    }

    private static boolean isValueSpec(String spec) {
        return !"/".equals(spec) && !spec.endsWith("X");
    }

    private static List<String> parseFormatSpecs(String formatLiteral) {
        String raw = unquote(formatLiteral);
        List<String> specs = new ArrayList<>();
        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }

            String upper = token.toUpperCase(Locale.ROOT);
            char kind = upper.charAt(0);
            if ("/".equals(upper)) {
                specs.add(upper);
            } else if (upper.endsWith("X") && Character.isDigit(upper.charAt(0))) {
                Integer.parseInt(upper.substring(0, upper.length() - 1));
                specs.add(upper);
            } else if (kind == 'I') {
                validateIntegerSpec(upper);
                specs.add(upper);
            } else if (kind == 'A') {
                if (upper.length() > 1) {
                    Integer.parseInt(upper.substring(1));
                }
                specs.add(upper);
            } else if (kind == 'L') {
                Integer.parseInt(upper.substring(1));
                specs.add(upper);
            } else if (kind == 'F' || kind == 'E') {
                int dot = upper.indexOf('.');
                if (dot < 0) {
                    throw new IllegalArgumentException("Unsupported format token: " + token);
                }
                Integer.parseInt(upper.substring(1, dot));
                Integer.parseInt(upper.substring(dot + 1));
                specs.add(upper);
            } else {
                throw new IllegalArgumentException("Unsupported format token: " + token);
            }
        }
        return specs;
    }

    private static int countValueSpecs(List<String> specs) {
        int count = 0;
        for (String spec : specs) {
            if (isValueSpec(spec)) {
                count++;
            }
        }
        return count;
    }

    private static void validateIntegerSpec(String spec) {
        int dot = spec.indexOf('.');
        if (dot < 0) {
            Integer.parseInt(spec.substring(1));
        } else {
            Integer.parseInt(spec.substring(1, dot));
            Integer.parseInt(spec.substring(dot + 1));
        }
    }

    private static String formatValue(String spec, Object value) {
        char kind = spec.charAt(0);
        return switch (kind) {
            case 'I' -> formatInteger(spec, value);
            case 'F' -> String.format(Locale.ROOT, "%" + spec.substring(1).toLowerCase(Locale.ROOT) + "f", asDouble(value));
            case 'E' -> String.format(Locale.ROOT, "%" + spec.substring(1).toLowerCase(Locale.ROOT) + "e", asDouble(value));
            case 'A' -> formatString(spec, value);
            case 'L' -> String.format(Locale.ROOT, "%" + spec.substring(1) + "b", asBoolean(value));
            default -> throw new IllegalArgumentException("Unsupported format token: " + spec);
        };
    }

    private static String formatInteger(String spec, Object value) {
        int number = asInt(value);
        int dot = spec.indexOf('.');
        if (dot < 0) {
            return String.format(Locale.ROOT, "%" + spec.substring(1) + "d", number);
        }

        int width = Integer.parseInt(spec.substring(1, dot));
        int minDigits = Integer.parseInt(spec.substring(dot + 1));
        String sign = number < 0 ? "-" : "";
        String digits = Integer.toString(Math.abs(number));
        if (digits.length() < minDigits) {
            digits = "0".repeat(minDigits - digits.length()) + digits;
        }
        String result = sign + digits;
        if (result.length() >= width) {
            return result;
        }
        return " ".repeat(width - result.length()) + result;
    }

    private static String formatString(String spec, Object value) {
        String text = String.valueOf(value);
        if (spec.length() == 1) {
            return text;
        }
        return String.format(Locale.ROOT, "%" + spec.substring(1) + "s", text);
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        throw new IllegalArgumentException("Expected integer value for format");
    }

    private static double asDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        throw new IllegalArgumentException("Expected real value for format");
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        throw new IllegalArgumentException("Expected Boolean value for format");
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
