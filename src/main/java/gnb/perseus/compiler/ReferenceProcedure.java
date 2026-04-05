package gnb.perseus.compiler;

/**
 * Interface representing a procedure variable with object/reference return type.
 */
@FunctionalInterface
public interface ReferenceProcedure {
    Object invoke(Object[] args);
}
