package gnb.perseus.runtime;

public final class FaultSupport {
    private FaultSupport() {
    }

    public static void fault(String message, double code) {
        throw new RuntimeException(message);
    }
}
