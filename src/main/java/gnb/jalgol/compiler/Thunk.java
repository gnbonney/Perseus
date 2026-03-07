package gnb.jalgol.compiler;

/**
 * Generic thunk interface used to implement call-by-name parameters.
 *
 * @param <T> the underlying Algol type (e.g. Integer, Double, String)
 */
public interface Thunk<T> {
    /**
     * Retrieve the current value of the parameter.
     */
    T get();

    /**
     * Assign a new value to the parameter.
     */
    void set(T v);
}
