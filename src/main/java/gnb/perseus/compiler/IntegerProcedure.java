package gnb.perseus.compiler;

/**
 * Interface representing a procedure variable with integer return type.
 * Implemented by generated ProcRef classes for integer procedures.
 */
public interface IntegerProcedure {
    int invoke(Object[] args);
}
