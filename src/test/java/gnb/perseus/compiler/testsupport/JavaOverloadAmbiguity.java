package gnb.perseus.compiler.testsupport;

import java.io.Serializable;

public class JavaOverloadAmbiguity {
    public String pick(Serializable value) {
        return "serial";
    }

    public String pick(CharSequence value) {
        return "chars";
    }
}
