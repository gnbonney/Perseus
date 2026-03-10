package gnb.jalgol.compiler;

/**
 * Interface representing a procedure variable with string return type.
 * Implemented by generated ProcRef classes for string procedures.
 */
public interface StringProcedure {
    String invoke(Object[] args);
}
