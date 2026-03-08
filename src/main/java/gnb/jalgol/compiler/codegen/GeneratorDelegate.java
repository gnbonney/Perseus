package gnb.jalgol.compiler.codegen;

import gnb.jalgol.compiler.antlr.AlgolParser;
import java.util.Map;

/**
 * Interface for logic delegates that work with CodeGenerator.
 */
public interface GeneratorDelegate {
    void setContext(ContextManager context);
}
