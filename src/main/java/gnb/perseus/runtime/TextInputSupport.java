// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.runtime;

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
}
