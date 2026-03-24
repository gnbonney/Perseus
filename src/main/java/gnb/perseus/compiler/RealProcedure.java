package gnb.perseus.compiler;

/**
 * Interface representing a procedure variable with real (double) return type.
 * Implemented by generated ProcRef classes for real procedures.
 */
public interface RealProcedure {
    double invoke(Object[] args);
}
