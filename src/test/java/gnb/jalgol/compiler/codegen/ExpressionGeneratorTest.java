package gnb.jalgol.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import gnb.jalgol.compiler.antlr.AlgolLexer;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.SymbolTableBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class ExpressionGeneratorTest {

    private ContextManager context;
    private ExpressionGenerator exprGen;

    @BeforeEach
    public void setUp() {
        context = new ContextManager("test", "gnb/jalgol/test", "TestClass", new HashMap<>(), new HashMap<>());
        exprGen = new ExpressionGenerator();
        exprGen.setContext(context);
    }

    @Test
    public void testGenerateIntLiteral() {
        AlgolParser.ExprContext ctx = parseExpr("42");
        String code = exprGen.generateExpr(ctx);
        assertTrue(code.contains("ldc 42"), "Should generate ldc for integer literal");
    }

    @Test
    public void testGenerateRealLiteral() {
        AlgolParser.ExprContext ctx = parseExpr("3.14");
        String code = exprGen.generateExpr(ctx);
        assertTrue(code.contains("ldc2_w 3.14"), "Should generate ldc2_w for real literal");
    }

    @Test
    public void testGenerateLoadVar() {
        context.getSymbolTable().put("x", "integer");
        context.getLocalIndex().put("x", 1);
        
        String code = exprGen.generateLoadVar("x");
        assertTrue(code.contains("iload 1"), "Should generate iload for integer variable");
    }

    @Test
    public void testGenerateLoadRealVar() {
        context.getSymbolTable().put("y", "real");
        context.getLocalIndex().put("y", 2);
        
        String code = exprGen.generateLoadVar("y");
        assertTrue(code.contains("dload 2"), "Should generate dload for real variable");
    }

    @Test
    public void testGenerateAddExpr() {
        AlgolParser.ExprContext ctx = parseExpr("1 + 2");
        String code = exprGen.generateExpr(ctx);
        assertTrue(code.contains("ldc 1"), "Should load 1");
        assertTrue(code.contains("ldc 2"), "Should load 2");
        assertTrue(code.contains("iadd"), "Should add integers");
    }

    @Test
    public void testGenerateThunkLoad() {
        // Simulating a call-by-name parameter (thunk)
        context.getSymbolTable().put("z", "thunk:integer");
        context.getLocalIndex().put("z", 5);
        
        String code = exprGen.generateLoadVar("z");
        assertTrue(code.contains("aload 5"), "Should load thunk object");
        assertTrue(code.contains("invokeinterface gnb/jalgol/runtime/Thunk/eval()Ljava/lang/Object; 1"), "Should call thunk eval");
        assertTrue(code.contains("checkcast java/lang/Integer"), "Should cast result to Integer");
        assertTrue(code.contains("invokevirtual java/lang/Integer/intValue()I"), "Should unbox result");
    }

    @Test
    public void testGenerateProcedureReferenceAsValue() {
        // Mock a procedure in the context
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("void");
        context.getProcedures().put("myProc", info);
        
        // Setup ProcedureGenerator and wire it to ExpressionGenerator
        ProcedureGenerator procGen = new ProcedureGenerator(context, exprGen);
        exprGen.setProcedureGenerator(procGen);

        AlgolParser.ExprContext ctx = parseExpr("myProc");
        String code = exprGen.generateExpr(ctx);
        
        // Should generate a new ProcRef object
        assertTrue(code.contains("new gnb/jalgol/test/TestClass$ProcRef0"), "Should instantiate a ProcRef class for the procedure");
        assertTrue(code.contains("invokenonvirtual gnb/jalgol/test/TestClass$ProcRef0/<init>()V"), "Should call ProcRef constructor");
    }

    private AlgolParser.ExprContext parseExpr(String input) {
        AlgolLexer lexer = new AlgolLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        AlgolParser parser = new AlgolParser(tokens);
        return parser.expr();
    }
}
