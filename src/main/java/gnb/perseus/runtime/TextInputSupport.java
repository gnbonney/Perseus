// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public final class TextInputSupport {
    private static final Scanner STDIN_SCANNER = new Scanner(System.in);

    private TextInputSupport() {
    }

    public static int ininteger(int channel) {
        if (channel >= 2) {
            return ChannelsSupport.inInteger(channel);
        }
        return STDIN_SCANNER.nextInt();
    }

    public static double inreal(int channel) {
        if (channel >= 2) {
            return ChannelsSupport.inReal(channel);
        }
        return STDIN_SCANNER.nextDouble();
    }

    public static int inchar(int channel, String text) {
        if (channel >= 2) {
            return ChannelsSupport.inChar(channel, text);
        }
        return text.indexOf(STDIN_SCANNER.next().charAt(0));
    }

    public static char[] informatKinds(String formatLiteral) {
        List<Character> kinds = parseInformatKinds(formatLiteral);
        char[] result = new char[kinds.size()];
        for (int i = 0; i < kinds.size(); i++) {
            result[i] = kinds.get(i);
        }
        return result;
    }

    public static Object[] informatValues(int channel, String formatLiteral) {
        List<Character> kinds = parseInformatKinds(formatLiteral);
        Object[] values = new Object[kinds.size()];
        for (int i = 0; i < kinds.size(); i++) {
            values[i] = switch (kinds.get(i)) {
                case 'I' -> Integer.valueOf(ininteger(channel));
                case 'F' -> Double.valueOf(inreal(channel));
                case 'A' -> inToken(channel);
                default -> throw new IllegalArgumentException("Unsupported informat specifier");
            };
        }
        return values;
    }

    private static List<Character> parseInformatKinds(String formatLiteral) {
        String raw = unquote(formatLiteral);
        List<Character> kinds = new ArrayList<>();
        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            String upper = token.toUpperCase(Locale.ROOT);
            char kind = upper.charAt(0);
            if (kind == 'I' || kind == 'A') {
                requireDigits(token, upper.substring(1));
                kinds.add(kind);
            } else if (kind == 'F') {
                int dot = upper.indexOf('.');
                if (dot < 0) {
                    throw new IllegalArgumentException("Unsupported format token: " + token);
                }
                requireDigits(token, upper.substring(1, dot));
                requireDigits(token, upper.substring(dot + 1));
                kinds.add(kind);
            } else {
                throw new IllegalArgumentException("Unsupported format token: " + token);
            }
        }
        return kinds;
    }

    private static void requireDigits(String token, String digits) {
        try {
            Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unsupported format token: " + token, e);
        }
    }

    private static String inToken(int channel) {
        if (channel >= 2) {
            return ChannelsSupport.inToken(channel);
        }
        return STDIN_SCANNER.next();
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
