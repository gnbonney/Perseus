// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.runtime;

import java.util.Scanner;

public final class TextInputSupport {
    private static final Scanner STDIN_SCANNER = new Scanner(System.in);

    private TextInputSupport() {
    }

    public static int ininteger(int channel) {
        return STDIN_SCANNER.nextInt();
    }

    public static double inreal(int channel) {
        return STDIN_SCANNER.nextDouble();
    }

    public static int inchar(int channel, String text) {
        return text.indexOf(STDIN_SCANNER.next().charAt(0));
    }
}
