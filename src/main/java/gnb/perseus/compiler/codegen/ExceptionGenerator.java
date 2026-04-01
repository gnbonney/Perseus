package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.ExceptionTypeResolver;
import gnb.perseus.compiler.antlr.PerseusParser;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Handles lowering of Perseus exception blocks to Jasmin `.catch` regions.
 */
public class ExceptionGenerator {
    private final Deque<ExceptionBlockInfo> exceptionBlockStack = new ArrayDeque<>();
    private final Function<String, String> labelGenerator;

    private static final class ExceptionBlockInfo {
        final PerseusParser.BlockContext blockCtx;
        final String startLabel;
        final String endLabel;
        final String afterLabel;
        final Map<PerseusParser.ExceptionHandlerContext, String> handlerLabels;

        ExceptionBlockInfo(PerseusParser.BlockContext blockCtx, String startLabel, String endLabel, String afterLabel,
                Map<PerseusParser.ExceptionHandlerContext, String> handlerLabels) {
            this.blockCtx = blockCtx;
            this.startLabel = startLabel;
            this.endLabel = endLabel;
            this.afterLabel = afterLabel;
            this.handlerLabels = handlerLabels;
        }
    }

    public ExceptionGenerator(Function<String, String> labelGenerator) {
        this.labelGenerator = labelGenerator;
    }

    public boolean isInsideExceptionBlock() {
        return !exceptionBlockStack.isEmpty();
    }

    public void enterBlock(PerseusParser.BlockContext ctx, StringBuilder activeOutput) {
        if (ctx.exceptionPart() == null) return;
        String startLabel = labelGenerator.apply("try_start");
        String endLabel = labelGenerator.apply("try_end");
        String afterLabel = labelGenerator.apply("try_after");
        Map<PerseusParser.ExceptionHandlerContext, String> handlerLabels = new LinkedHashMap<>();
        for (PerseusParser.ExceptionHandlerContext handler : ctx.exceptionPart().exceptionHandler()) {
            handlerLabels.put(handler, labelGenerator.apply("catch"));
        }
        ExceptionBlockInfo info = new ExceptionBlockInfo(ctx, startLabel, endLabel, afterLabel, handlerLabels);
        exceptionBlockStack.push(info);
        activeOutput.append(startLabel).append(":\n");
    }

    public void exitBlock(PerseusParser.BlockContext ctx, StringBuilder activeOutput) {
        if (ctx.exceptionPart() == null) return;
        ExceptionBlockInfo info = exceptionBlockStack.pop();
        for (PerseusParser.ExceptionHandlerContext handler : ctx.exceptionPart().exceptionHandler()) {
            activeOutput.append(".catch ")
                        .append(exceptionPatternToJvmType(handler.exceptionPattern()))
                        .append(" from ").append(info.startLabel)
                        .append(" to ").append(info.endLabel)
                        .append(" using ").append(info.handlerLabels.get(handler))
                        .append("\n");
        }
        activeOutput.append(info.afterLabel).append(":\n");
    }

    public void exitCompoundStatement(PerseusParser.CompoundStatementContext ctx, StringBuilder activeOutput) {
        if (ctx.getParent() instanceof PerseusParser.BlockContext blockCtx
                && blockCtx.exceptionPart() != null
                && !exceptionBlockStack.isEmpty()
                && exceptionBlockStack.peek().blockCtx == blockCtx) {
            ExceptionBlockInfo info = exceptionBlockStack.peek();
            activeOutput.append("goto ").append(info.afterLabel).append("\n");
            activeOutput.append(info.endLabel).append(":\n");
        }
    }

    public void enterExceptionHandler(PerseusParser.ExceptionHandlerContext ctx, StringBuilder activeOutput) {
        if (exceptionBlockStack.isEmpty()) return;
        ExceptionBlockInfo info = exceptionBlockStack.peek();
        String handlerLabel = info.handlerLabels.get(ctx);
        if (handlerLabel != null) {
            activeOutput.append(handlerLabel).append(":\n");
        }
    }

    public void exitExceptionHandler(PerseusParser.ExceptionHandlerContext ctx, StringBuilder activeOutput) {
        if (exceptionBlockStack.isEmpty()) return;
        ExceptionBlockInfo info = exceptionBlockStack.peek();
        activeOutput.append("goto ").append(info.afterLabel).append("\n");
    }

    private String exceptionPatternToJvmType(PerseusParser.ExceptionPatternContext ctx) {
        return ExceptionTypeResolver.toInternalJavaName(ctx);
    }
}
