package gnb.perseus.compiler;

/**
 * Interface representing a procedure variable with void return type.
 * Implemented by generated ProcRef classes for void procedures.
 */
public interface VoidProcedure {
    void invoke(Object[] args);
}
