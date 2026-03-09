package gnb.jalgol.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import gnb.jalgol.compiler.antlr.AlgolLexer;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.SymbolTableBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ProcedureGeneratorTest {

    private ContextManager context;
    private ExpressionGenerator exprGen;
    private ProcedureGenerator procGen;

    @BeforeEach
    public void setUp() {
        context = new ContextManager("test", "gnb/jalgol/test", "TestClass", new java.util.HashMap<>(), new java.util.HashMap<>());
        exprGen = new ExpressionGenerator();
        exprGen.setContext(context);
        procGen = new ProcedureGenerator(context, exprGen);
    }

    @Test
    public void testGenerateSimpleProcedureCall() {
        // Setup a procedure in the context
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("void");
        context.getProcedures().put("myProc", info);

        String callCode = procGen.generateProcedureCall("myProc", Collections.emptyList(), true);
        
        assertTrue(callCode.contains("invokestatic gnb/jalgol/test/TestClass/myProc()V"), 
            "Should generate a static call to the procedure");
    }

    @Test
    public void testGenerateProcedureWithIntegerParam() {
        AlgolParser.ProgramContext prog = parse("begin procedure p(x); value x; integer x; x := 1; p(10) end");
        
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("void");
        info.paramNames.add("x");
        info.valueParams.add("x");
        info.paramTypes.put("x", "integer");
        context.getProcedures().put("p", info);

        // find the procedureCall inside the compoundStatement
        AlgolParser.ProcedureCallContext callCtx = null;
        for (AlgolParser.StatementContext stmt : prog.compoundStatement().statement()) {
            if (stmt.procedureCall() != null) {
                callCtx = stmt.procedureCall();
                break;
            }
        }
        assertNotNull(callCtx, "Should find the procedure call p(10)");
        
        String callCode = procGen.generateProcedureCall("p", callCtx.argList().arg(), true);
        
        assertTrue(callCode.contains("ldc 10"), "Should load the integer argument (ExpressionGenerator uses ldc for literals)");
        assertTrue(callCode.contains("invokestatic gnb/jalgol/test/TestClass/p(I)V"), "Should call with integer descriptor");
    }

    @Test
    public void testEnterExitProcedureDecl() {
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("integer");
        context.getProcedures().put("myFunc", info);

        AlgolParser.ProgramContext prog = parse("begin integer procedure myFunc; myFunc := 42; myFunc end");
        AlgolParser.ProcedureDeclContext decl = prog.compoundStatement().statement(0).procedureDecl();

        procGen.enterProcedureDecl(decl);
        
        StringBuilder activeOutput = context.getActiveOutput();
        assertNotNull(activeOutput);
        assertTrue(activeOutput.toString().contains(".method public static myFunc()I"), "Method header should be generated");
        assertTrue(activeOutput.toString().contains(".limit locals 1"), "Local for return value should be allocated");

        procGen.exitProcedureDecl(decl);
        
        String methodCode = context.getProcMethods().get(0);
        assertTrue(methodCode.contains("ireturn"), "Should contain integer return");
        assertTrue(methodCode.contains(".end method"), "Should contain end method");
    }

    private AlgolParser.ProgramContext parse(String input) {
        AlgolLexer lexer = new AlgolLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        AlgolParser parser = new AlgolParser(tokens);
        return parser.program();
    }
}
