// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.runtime;

import java.io.PrintStream;

public final class TextOutputSupport {
    private TextOutputSupport() {
    }

    private static PrintStream streamForChannel(int channel) {
        return channel == 0 ? System.err : System.out;
    }

    public static void outstring(int channel, String value) {
        streamForChannel(channel).print(value);
    }

    public static void outreal(int channel, double value) {
        streamForChannel(channel).print(value);
    }

    public static void outinteger(int channel, int value) {
        streamForChannel(channel).print(value);
    }

    public static void outterminator(int channel) {
        streamForChannel(channel).print(" ");
    }

    public static void outchar(int channel, String value, int position) {
        streamForChannel(channel).print(value.charAt(position));
    }
}
