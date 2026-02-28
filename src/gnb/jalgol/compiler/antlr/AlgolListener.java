// Generated from Algol.g4 by ANTLR 4.4
package gnb.jalgol.compiler.antlr;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link AlgolParser}.
 */
public interface AlgolListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link AlgolParser#argList}.
	 * @param ctx the parse tree
	 */
	void enterArgList(@NotNull AlgolParser.ArgListContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#argList}.
	 * @param ctx the parse tree
	 */
	void exitArgList(@NotNull AlgolParser.ArgListContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(@NotNull AlgolParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(@NotNull AlgolParser.IdentifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#string}.
	 * @param ctx the parse tree
	 */
	void enterString(@NotNull AlgolParser.StringContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#string}.
	 * @param ctx the parse tree
	 */
	void exitString(@NotNull AlgolParser.StringContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#procedureCall}.
	 * @param ctx the parse tree
	 */
	void enterProcedureCall(@NotNull AlgolParser.ProcedureCallContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#procedureCall}.
	 * @param ctx the parse tree
	 */
	void exitProcedureCall(@NotNull AlgolParser.ProcedureCallContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#compoundStatement}.
	 * @param ctx the parse tree
	 */
	void enterCompoundStatement(@NotNull AlgolParser.CompoundStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#compoundStatement}.
	 * @param ctx the parse tree
	 */
	void exitCompoundStatement(@NotNull AlgolParser.CompoundStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#arg}.
	 * @param ctx the parse tree
	 */
	void enterArg(@NotNull AlgolParser.ArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#arg}.
	 * @param ctx the parse tree
	 */
	void exitArg(@NotNull AlgolParser.ArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(@NotNull AlgolParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(@NotNull AlgolParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#unsignedInt}.
	 * @param ctx the parse tree
	 */
	void enterUnsignedInt(@NotNull AlgolParser.UnsignedIntContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#unsignedInt}.
	 * @param ctx the parse tree
	 */
	void exitUnsignedInt(@NotNull AlgolParser.UnsignedIntContext ctx);
	/**
	 * Enter a parse tree produced by {@link AlgolParser#program}.
	 * @param ctx the parse tree
	 */
	void enterProgram(@NotNull AlgolParser.ProgramContext ctx);
	/**
	 * Exit a parse tree produced by {@link AlgolParser#program}.
	 * @param ctx the parse tree
	 */
	void exitProgram(@NotNull AlgolParser.ProgramContext ctx);
}