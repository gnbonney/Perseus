// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.runtime;

public final class MathConstantsSupport {
    private MathConstantsSupport() {
    }

    public static double maxreal() {
        return Double.MAX_VALUE;
    }

    public static double minreal() {
        return Double.MIN_VALUE;
    }

    public static int maxint() {
        return Integer.MAX_VALUE;
    }

    public static double epsilon() {
        return Double.MIN_NORMAL;
    }
}
