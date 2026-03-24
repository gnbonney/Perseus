package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.antlr.AlgolParser;
import java.util.Map;

/**
 * Interface for logic delegates that work with CodeGenerator.
 */
public interface GeneratorDelegate {
    void setContext(ContextManager context);
}
