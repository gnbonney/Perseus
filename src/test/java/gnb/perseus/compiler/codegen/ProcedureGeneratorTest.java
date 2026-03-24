package gnb.perseus.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import gnb.perseus.compiler.antlr.PerseusLexer;
import gnb.perseus.compiler.antlr.PerseusParser;
import gnb.perseus.compiler.SymbolTableBuilder;
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
        context = new ContextManager("test", "gnb/perseus/test", "TestClass", new java.util.HashMap<>(), new java.util.HashMap<>());
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
        
        assertTrue(callCode.contains("invokestatic gnb/perseus/test/TestClass/myProc()V"), 
            "Should generate a static call to the procedure");
    }

    @Test
    public void testGenerateProcedureWithIntegerParam() {
        PerseusParser.ProgramContext prog = parse("begin procedure p(x); value x; integer x; x := 1; p(10) end");
        
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("void");
        info.paramNames.add("x");
        info.valueParams.add("x");
        info.paramTypes.put("x", "integer");
        context.getProcedures().put("p", info);

        // find the procedureCall inside the compoundStatement
        PerseusParser.ProcedureCallContext callCtx = null;
        for (PerseusParser.StatementContext stmt : prog.compoundStatement().statement()) {
            if (stmt.procedureCall() != null) {
                callCtx = stmt.procedureCall();
                break;
            }
        }
        assertNotNull(callCtx, "Should find the procedure call p(10)");
        
        String callCode = procGen.generateProcedureCall("p", callCtx.argList().arg(), true);
        
        assertTrue(callCode.contains("ldc 10"), "Should load the integer argument (ExpressionGenerator uses ldc for literals)");
        assertTrue(callCode.contains("invokestatic gnb/perseus/test/TestClass/p(I)V"), "Should call with integer descriptor");
    }

    @Test
    public void testEnterExitProcedureDecl() {
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("integer");
        context.getProcedures().put("myFunc", info);

        PerseusParser.ProgramContext prog = parse("begin integer procedure myFunc; myFunc := 42; myFunc end");
        PerseusParser.ProcedureDeclContext decl = prog.compoundStatement().statement(0).procedureDecl();

        procGen.enterProcedureDecl(decl);
        
        StringBuilder activeOutput = context.getActiveOutput();
        assertNotNull(activeOutput);
        assertTrue(activeOutput.toString().contains(".method public static myFunc()I"), "Method header should be generated");

        procGen.exitProcedureDecl(decl);
        
        String methodCode = context.getProcMethods().get(0);
        assertTrue(methodCode.contains("ireturn"), "Should contain integer return");
        assertTrue(methodCode.contains(".end method"), "Should contain end method");
    }

    private PerseusParser.ProgramContext parse(String input) {
        PerseusLexer lexer = new PerseusLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PerseusParser parser = new PerseusParser(tokens);
        return parser.program();
    }
}

