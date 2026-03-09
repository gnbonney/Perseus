package gnb.jalgol.compiler.codegen;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import gnb.jalgol.compiler.antlr.AlgolLexer;
import gnb.jalgol.compiler.antlr.AlgolParser;
import gnb.jalgol.compiler.SymbolTableBuilder;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

public class StatementGeneratorTest {

    private ContextManager context;
    private ExpressionGenerator exprGen;
    private ProcedureGenerator procGen;
    private StatementGenerator stmtGen;

    @BeforeEach
    public void setUp() {
        context = new ContextManager("test", "gnb/jalgol/test", "TestClass", new HashMap<>(), new HashMap<>());
        exprGen = new ExpressionGenerator();
        exprGen.setContext(context);
        procGen = new ProcedureGenerator(context, exprGen);
        exprGen.setProcedureGenerator(procGen); // Fix: Wire procGen for RHS references
        stmtGen = new StatementGenerator(exprGen);
        stmtGen.setProcedureGenerator(procGen);
        stmtGen.setContext(context);
    }

    @Test
    public void testGenerateSimpleAssignment() {
        context.getSymbolTable().put("x", "integer");
        context.getLocalIndex().put("x", 1);

        AlgolParser.ProgramContext prog = parse("begin x := 42 end");
        AlgolParser.AssignmentContext assignCtx = (AlgolParser.AssignmentContext) prog.compoundStatement().statement(0).assignment();
        
        String code = stmtGen.generateAssignment(assignCtx, "gnb/jalgol/test", "TestClass");
        
        assertTrue(code.contains("ldc 42"), "Should load the value 42");
        assertTrue(code.contains("istore 1"), "Should store into local variable x at slot 1");
    }

    @Test
    public void testIfStatementLabels() {
        AlgolParser.ProgramContext prog = parse("begin if x > 0 then x := 1 end");
        AlgolParser.IfStatementContext ifCtx = (AlgolParser.IfStatementContext) prog.compoundStatement().statement(0).ifStatement();
        
        StringBuilder output = new StringBuilder();
        stmtGen.enterIfStatement(ifCtx, output);
        
        String code = output.toString();
        assertTrue(code.contains("if_icmpgt then_"), "Should contain comparison jump to then label");
        assertTrue(code.contains("goto endif_"), "Should contain jump to endif label");
        assertTrue(code.contains("then_"), "Should contain then label definition");

        stmtGen.exitIfStatement(ifCtx, output);
        assertTrue(output.toString().contains("endif_"), "Should contain endif label definition");
    }

    @Test
    public void testForStatementStepUntil() {
        context.getSymbolTable().put("i", "integer");
        context.getLocalIndex().put("i", 2);

        AlgolParser.ProgramContext prog = parse("begin for i := 1 step 1 until 10 do x := i end");
        AlgolParser.ForStatementContext forCtx = (AlgolParser.ForStatementContext) prog.compoundStatement().statement(0).forStatement();
        
        StringBuilder output = new StringBuilder();
        stmtGen.enterForStatement(forCtx, output);
        
        String code = output.toString();
        assertTrue(code.contains("ldc 1"), "Should initialize i with 1");
        assertTrue(code.contains("istore 2"), "Should store initial value in i");
        assertTrue(code.contains("loop_"), "Should define loop label");
        assertTrue(code.contains("if_icmpgt endfor_"), "Should check upper bound");

        stmtGen.exitForStatement(forCtx, output);
        assertTrue(output.toString().contains("goto loop_"), "Should jump back to loop start");
        assertTrue(output.toString().contains("endfor_"), "Should define endfor label");
    }

    @Test
    public void testProcedureVariableAssignmentBug() {
        // Bug: StatementGenerator.generateAssignment emits istore -1 for procedure assignments
        context.getSymbolTable().put("P", "procedure:void");
        context.getLocalIndex().put("P", 3);
        
        // Mock a procedure 'myProc' in the context
        SymbolTableBuilder.ProcInfo info = new SymbolTableBuilder.ProcInfo("void");
        context.getProcedures().put("myProc", info);

        AlgolParser.ProgramContext prog = parse("begin P := myProc end");
        AlgolParser.AssignmentContext assignCtx = (AlgolParser.AssignmentContext) prog.compoundStatement().statement(0).assignment();
        
        // We need ExpressionGenerator to handle the RHS 'myProc' as a reference
        // Actually the bug report says generateAssignment emits istore -1.
        String code = stmtGen.generateAssignment(assignCtx, "gnb/jalgol/test", "TestClass");
        
        assertTrue(code.contains("astore 3"), "Should use astore for procedure variable, not istore -1");
        assertTrue(!code.contains("istore -1"), "Should not contain istore -1");
    }

    @Test
    public void testProcedureVariableCallBug() {
        // Bug: StatementGenerator.exitProcedureCall doesn't route variable-based calls to generateProcedureVariableCall
        context.getSymbolTable().put("P", "procedure:void");
        context.getLocalIndex().put("P", 4);
        
        AlgolParser.ProgramContext prog = parse("begin P end");
        AlgolParser.ProcedureCallContext callCtx = (AlgolParser.ProcedureCallContext) prog.compoundStatement().statement(0).procedureCall();
        
        String code = stmtGen.exitProcedureCall(callCtx);
        
        // Should contain invokeinterface for ProcedureInterfaces
        assertTrue(code.contains("invokeinterface gnb/jalgol/compiler/VoidProcedure/invoke"), "Should call variable via interface");
    }

    private AlgolParser.ProgramContext parse(String input) {
        AlgolLexer lexer = new AlgolLexer(CharStreams.fromString(input));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        AlgolParser parser = new AlgolParser(tokens);
        return parser.program();
    }
}
