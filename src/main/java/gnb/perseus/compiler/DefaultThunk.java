// Copyright (c) 2017-2026 Greg Bonney

package gnb.perseus.compiler;

/**
 * A simple Thunk implementation that always returns 0 (integer) and ignores set().
 *
 * This is used as a safe fallback when the compiled code would otherwise attempt
 * to invoke a null thunk reference due to missing environment bridging.
 */
public class DefaultThunk implements Thunk {
    @Override
    public Object get() {
        return Integer.valueOf(0);
    }

    @Override
    public void set(Object value) {
        // no-op
    }
}
