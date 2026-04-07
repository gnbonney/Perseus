package gnb.perseus.compiler.codegen;

import gnb.perseus.compiler.antlr.PerseusParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Handles the first slice of dynamic channel built-ins:
 * openfile/openstring/closefile plus channel-aware output and instring lowering.
 */
public class ChannelIOGenerator {
    private final String packageName;
    private final String className;
    private final Map<Integer, FileChannelBinding> constantFileChannels = new LinkedHashMap<>();
    private final Map<Integer, String> constantStringChannels = new LinkedHashMap<>();

    private record FileChannelBinding(String filenameLiteral, String mode) {}

    public ChannelIOGenerator(String packageName, String className) {
        this.packageName = packageName;
        this.className = className;
    }

    public boolean tryEmitProcedureCall(
            String name,
            List<PerseusParser.ArgContext> args,
            StringBuilder activeOutput,
            Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable,
            Map<String, String> mainSymbolTable,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ExprContext, String> exprTypeResolver,
            Function<String, Integer> allocateNewLocal,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            BiFunction<String, String, String> staticFieldName) {
        return switch (name) {
            case "openfile" -> emitOpenFile(args, activeOutput, generateExpr);
            case "openstring" -> emitOpenString(args, activeOutput);
            case "closefile" -> emitCloseFile(args, activeOutput, generateExpr);
            case "outstring" -> emitOutString(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outreal" -> emitOutReal(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outinteger" -> emitOutInteger(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outterminator" -> emitOutTerminator(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                      currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outformat" -> emitOutformat(args, activeOutput, generateExpr, exprTypeResolver, getChannelStream,
                      lookupVarType, currentLocalIndex, staticFieldName, allocateNewLocal);
            case "informat" -> emitInformat(args, activeOutput, generateExpr, allocateNewLocal, currentLocalIndex,
                    currentSymbolTable, mainSymbolTable, staticFieldName);
            case "instring" -> emitInstring(args, activeOutput, generateExpr, allocateNewLocal, currentLocalIndex,
                    currentSymbolTable, mainSymbolTable, staticFieldName);
            default -> false;
        };
    }

    private Integer getConstantChannelValue(PerseusParser.ArgContext channelArg) {
        if (channelArg == null || channelArg.expr() == null) {
            return null;
        }
        PerseusParser.ExprContext expr = channelArg.expr();
        if (expr instanceof PerseusParser.IntLiteralExprContext intExpr) {
            try {
                return Integer.parseInt(intExpr.unsignedInt().getText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String getStringLiteralText(PerseusParser.ArgContext arg) {
        if (arg == null || arg.expr() == null) {
            return null;
        }
        if (arg.expr() instanceof PerseusParser.StringLiteralExprContext strExpr) {
            return strExpr.string().getText();
        }
        return null;
    }

    private String getVariableName(PerseusParser.ArgContext arg) {
        if (arg == null || arg.expr() == null) {
            return null;
        }
        if (arg.expr() instanceof PerseusParser.VarExprContext varExpr) {
            return varExpr.identifier().getText();
        }
        return null;
    }

    private String generateFormattedString(PerseusParser.ExprContext formatExpr, List<PerseusParser.ArgContext> valueArgs,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ExprContext, String> exprTypeResolver,
            StringBuilder activeOutput) {
        StringBuilder sb = new StringBuilder();
        sb.append(generateExpr.apply(formatExpr));
        sb.append("ldc ").append(valueArgs.size()).append("\n");
        sb.append("anewarray java/lang/Object\n");
        for (int i = 0; i < valueArgs.size(); i++) {
            PerseusParser.ExprContext expr = valueArgs.get(i).expr();
            String exprType = expr != null ? exprTypeResolver.apply(expr) : "integer";
            sb.append("dup\n");
            sb.append("ldc ").append(i).append("\n");
            sb.append(generateExpr.apply(expr));
            if ("real".equals(exprType)) {
                sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
            } else if ("integer".equals(exprType)) {
                sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
            } else if ("boolean".equals(exprType)) {
                sb.append("invokestatic java/lang/Boolean/valueOf(Z)Ljava/lang/Boolean;\n");
            } else {
                // Strings and references are already object values at the JVM level.
            }
            sb.append("aastore\n");
        }
        sb.append("ldc 1\n");
        sb.append("ldc ").append(valueArgs.size()).append("\n");
        sb.append("ldc ").append(valueArgs.size()).append("\n");
        sb.append("invokestatic perseus/io/TextOutput/formatvalues(Ljava/lang/String;[Ljava/lang/Object;III)Ljava/lang/String;\n");
        return sb.toString();
    }

    private boolean emitOpenFile(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr) {
        if (args.size() < 3) {
            activeOutput.append("; ERROR: openfile requires channel, filename, and mode\n");
            return true;
        }
        Integer channel = getConstantChannelValue(args.get(0));
        String filenameLiteral = getStringLiteralText(args.get(1));
        String modeLiteral = getStringLiteralText(args.get(2));
        if (channel != null && filenameLiteral != null && modeLiteral != null) {
            String mode = switch (modeLiteral) {
                case "\"w\"" -> "w";
                case "\"r\"" -> "r";
                case "\"a\"" -> "a";
                default -> null;
            };
            if (mode != null) {
                constantFileChannels.put(channel, new FileChannelBinding(filenameLiteral, mode));
                constantStringChannels.remove(channel);
            }
        }
        appendChannelValue(args.get(0), activeOutput, generateExpr);
        activeOutput.append(args.get(1).expr() != null ? generateExpr.apply(args.get(1).expr()) : "ldc \"\"\n");
        activeOutput.append(args.get(2).expr() != null ? generateExpr.apply(args.get(2).expr()) : "ldc \"\"\n");
            activeOutput.append("invokestatic perseus/io/Channels/openfile(ILjava/lang/String;Ljava/lang/String;)V\n");
        return true;
    }

    private boolean emitOpenString(List<PerseusParser.ArgContext> args, StringBuilder activeOutput) {
        if (args.size() < 2) {
            activeOutput.append("; ERROR: openstring requires channel and target string variable\n");
            return true;
        }
        Integer channel = getConstantChannelValue(args.get(0));
        String variableName = getVariableName(args.get(1));
        if (channel == null || variableName == null) {
            activeOutput.append("; ERROR: openstring currently requires a constant channel and a string variable\n");
            return true;
        }
        constantStringChannels.put(channel, variableName);
        constantFileChannels.remove(channel);
        return true;
    }

    private boolean emitCloseFile(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr) {
        if (args.isEmpty()) {
            activeOutput.append("; ERROR: closefile requires a channel\n");
            return true;
        }
        Integer channel = getConstantChannelValue(args.get(0));
        if (channel != null) {
            constantFileChannels.remove(channel);
            constantStringChannels.remove(channel);
        }
        appendChannelValue(args.get(0), activeOutput, generateExpr);
        activeOutput.append("invokestatic perseus/io/Channels/closefile(I)V\n");
        return true;
    }

    private boolean emitOutString(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName,
            Function<String, Integer> allocateNewLocal) {
        PerseusParser.ArgContext channelArg = args.size() > 1 ? args.get(0) : null;
        if (!emitStringOrFileOutput(channelArg, "Ljava/lang/String;",
                generateExpr.apply(args.get(args.size() - 1).expr()), activeOutput, lookupVarType, currentLocalIndex,
                staticFieldName, allocateNewLocal)) {
            appendChannelValue(channelArg, activeOutput, generateExpr);
            activeOutput.append(generateExpr.apply(args.get(args.size() - 1).expr()))
                    .append("invokestatic perseus/io/TextOutput/outstring(ILjava/lang/String;)V\n");
        }
        return true;
    }

    private boolean emitOutReal(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName,
            Function<String, Integer> allocateNewLocal) {
        PerseusParser.ArgContext channelArg = args.size() > 1 ? args.get(0) : null;
        if (!emitStringOrFileOutput(channelArg, "D",
                generateExpr.apply(args.get(args.size() - 1).expr()), activeOutput, lookupVarType, currentLocalIndex,
                staticFieldName, allocateNewLocal)) {
            appendChannelValue(channelArg, activeOutput, generateExpr);
            activeOutput.append(generateExpr.apply(args.get(args.size() - 1).expr()))
                    .append("invokestatic perseus/io/TextOutput/outreal(ID)V\n");
        }
        return true;
    }

    private boolean emitOutInteger(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName,
            Function<String, Integer> allocateNewLocal) {
        PerseusParser.ArgContext channelArg = args.size() > 1 ? args.get(0) : null;
        if (!emitStringOrFileOutput(channelArg, "I",
                generateExpr.apply(args.get(args.size() - 1).expr()), activeOutput, lookupVarType, currentLocalIndex,
                staticFieldName, allocateNewLocal)) {
            appendChannelValue(channelArg, activeOutput, generateExpr);
            activeOutput.append(generateExpr.apply(args.get(args.size() - 1).expr()))
                    .append("invokestatic perseus/io/TextOutput/outinteger(II)V\n");
        }
        return true;
    }

    private boolean emitOutTerminator(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName,
            Function<String, Integer> allocateNewLocal) {
        PerseusParser.ArgContext channelArg = args.size() > 0 ? args.get(0) : null;
        if (!emitStringOrFileOutput(channelArg, "Ljava/lang/String;", "ldc \" \"\n", activeOutput, lookupVarType,
                currentLocalIndex, staticFieldName, allocateNewLocal)) {
            appendChannelValue(channelArg, activeOutput, generateExpr);
            activeOutput.append("invokestatic perseus/io/TextOutput/outterminator(I)V\n");
        }
        return true;
    }

    private void appendChannelValue(PerseusParser.ArgContext channelArg, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr) {
        if (channelArg == null || channelArg.expr() == null) {
            activeOutput.append("iconst_1\n");
            return;
        }
        activeOutput.append(generateExpr.apply(channelArg.expr()));
    }

    private boolean emitOutformat(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ExprContext, String> exprTypeResolver,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName,
            Function<String, Integer> allocateNewLocal) {
        if (args.size() < 2) {
            activeOutput.append("; ERROR: outformat requires at least a channel and format string\n");
            return true;
        }
        PerseusParser.ExprContext formatExpr = args.get(1).expr();
        if (formatExpr == null) {
            activeOutput.append("; ERROR: outformat requires a format string\n");
            return true;
        }
        String formattedValue = generateFormattedString(formatExpr, args.subList(2, args.size()), generateExpr, exprTypeResolver,
                activeOutput);
        PerseusParser.ArgContext channelArg = args.get(0);
        if (emitStringOrFileOutput(channelArg, "Ljava/lang/String;", formattedValue, activeOutput, lookupVarType,
                currentLocalIndex, staticFieldName, allocateNewLocal)) {
            return true;
        }
        appendChannelValue(channelArg, activeOutput, generateExpr);
        activeOutput.append(formattedValue)
                .append("invokestatic perseus/io/TextOutput/outstring(ILjava/lang/String;)V\n");
        return true;
    }

    private boolean emitInstring(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<String, Integer> allocateNewLocal,
            Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable,
            Map<String, String> mainSymbolTable,
            BiFunction<String, String, String> staticFieldName) {
        if (!emitFileChannelInstring(args, activeOutput, generateExpr, allocateNewLocal, currentLocalIndex, currentSymbolTable,
                mainSymbolTable, staticFieldName)) {
            PerseusParser.ExprContext varExpr = args.get(1).expr();
            if (varExpr instanceof PerseusParser.VarExprContext) {
                String varName = ((PerseusParser.VarExprContext) varExpr).identifier().getText();
                Integer varSlot = currentLocalIndex.get(varName);
                String varType = currentSymbolTable.get(varName);
                if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:")
                        && !varType.startsWith("thunk:")) {
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                            .append("/__scanner Ljava/util/Scanner;\n")
                            .append("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;\n")
                            .append("putstatic ").append(packageName).append("/").append(className)
                            .append("/").append(varName).append(" Ljava/lang/String;\n");
                } else if (varSlot == null) {
                    activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
                } else {
                    activeOutput.append("getstatic ").append(packageName).append("/").append(className)
                            .append("/__scanner Ljava/util/Scanner;\n")
                            .append("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;\n")
                            .append("astore ").append(varSlot).append("\n");
                }
            } else {
                activeOutput.append("; ERROR: instring requires a variable as second argument\n");
            }
        }
        return true;
    }

    private boolean emitInformat(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<String, Integer> allocateNewLocal,
            Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable,
            Map<String, String> mainSymbolTable,
            BiFunction<String, String, String> staticFieldName) {
        if (args.size() < 2) {
            activeOutput.append("; ERROR: informat requires at least a channel and format string\n");
            return true;
        }
        StringBuilder expectedKinds = new StringBuilder();
        List<String> targetTypes = new ArrayList<>();
        List<String> targetNames = new ArrayList<>();

        for (int i = 2; i < args.size(); i++) {
            String varName = getVariableName(args.get(i));
            if (varName == null) {
                activeOutput.append("; ERROR: informat requires variables after the format string\n");
                return true;
            }
            String varType = currentSymbolTable.get(varName);
            if (varType == null && mainSymbolTable != null) {
                varType = mainSymbolTable.get(varName);
            }
            if ("integer".equals(varType)) {
                expectedKinds.append('I');
            } else if ("real".equals(varType)) {
                expectedKinds.append('F');
            } else if ("string".equals(varType)) {
                expectedKinds.append('A');
            } else {
                activeOutput.append("; ERROR: informat target must be an integer, real, or string variable\n");
                return true;
            }
            targetTypes.add(varType);
            targetNames.add(varName);
        }

        Integer valuesSlot = allocateNewLocal.apply("informatValues");
        activeOutput.append("ldc ").append(targetTypes.size()).append("\n")
                .append("anewarray java/lang/Object\n")
                .append("astore ").append(valuesSlot).append("\n");
        appendChannelValue(args.get(0), activeOutput, generateExpr);
        activeOutput.append(generateExpr.apply(args.get(1).expr()))
                .append("aload ").append(valuesSlot).append("\n")
                .append("ldc 1\n")
                .append("ldc ").append(targetTypes.size()).append("\n")
                .append("ldc \"").append(expectedKinds).append("\"\n")
                .append("invokestatic perseus/io/TextInput/informatvalues(ILjava/lang/String;[Ljava/lang/Object;IILjava/lang/String;)V\n");

        for (int i = 0; i < targetTypes.size(); i++) {
            String varName = targetNames.get(i);
            Integer varSlot = currentLocalIndex.get(varName);
            String varType = targetTypes.get(i);

            activeOutput.append("aload ").append(valuesSlot).append("\n")
                    .append("ldc ").append(i).append("\n")
                    .append("aaload\n");
            switch (varType) {
                case "integer" -> activeOutput.append("checkcast java/lang/Integer\n")
                        .append("invokevirtual java/lang/Integer/intValue()I\n");
                case "real" -> activeOutput.append("checkcast java/lang/Double\n")
                        .append("invokevirtual java/lang/Double/doubleValue()D\n");
                case "string" -> activeOutput.append("checkcast java/lang/String\n");
                default -> {
                    activeOutput.append("; ERROR: unsupported informat target type\n");
                    continue;
                }
            }

            if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:")
                    && !varType.startsWith("thunk:")) {
                String desc = "real".equals(varType) ? "D" : "string".equals(varType) ? "Ljava/lang/String;" : "I";
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                        .append("/").append(staticFieldName.apply(varName, varType)).append(" ").append(desc).append("\n");
            } else if (varSlot == null) {
                activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
            } else {
                String store = "real".equals(varType) ? "dstore " : "string".equals(varType) ? "astore " : "istore ";
                activeOutput.append(store).append(varSlot).append("\n");
            }
        }
        return true;
    }

    private boolean emitStringChannelWrite(int channel, String valueCode, StringBuilder activeOutput,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName) {
        String variableName = constantStringChannels.get(channel);
        if (variableName == null) {
            return false;
        }
        String varType = lookupVarType.apply(variableName);
        if (!"string".equals(varType)) {
            activeOutput.append("; ERROR: openstring target must be a string variable\n");
            return true;
        }

        Integer slot = currentLocalIndex.get(variableName);
        if (slot != null) {
            activeOutput.append("new java/lang/StringBuilder\n")
                    .append("dup\n")
                    .append("invokespecial java/lang/StringBuilder/<init>()V\n")
                    .append("aload ").append(slot).append("\n")
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    .append(valueCode)
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    .append("invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;\n")
                    .append("astore ").append(slot).append("\n");
        } else {
            activeOutput.append("new java/lang/StringBuilder\n")
                    .append("dup\n")
                    .append("invokespecial java/lang/StringBuilder/<init>()V\n")
                    .append("getstatic ").append(packageName).append("/").append(className)
                    .append("/").append(staticFieldName.apply(variableName, varType)).append(" Ljava/lang/String;\n")
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    .append(valueCode)
                    .append("invokevirtual java/lang/StringBuilder/append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n")
                    .append("invokevirtual java/lang/StringBuilder/toString()Ljava/lang/String;\n")
                    .append("putstatic ").append(packageName).append("/").append(className)
                    .append("/").append(staticFieldName.apply(variableName, varType)).append(" Ljava/lang/String;\n");
        }
        return true;
    }

    private boolean emitFileChannelPrint(int channel, String printDescriptor, String valueCode, StringBuilder activeOutput,
            Function<String, Integer> allocateNewLocal) {
        FileChannelBinding binding = constantFileChannels.get(channel);
        if (binding == null) {
            return false;
        }
        if (!"w".equals(binding.mode())) {
            activeOutput.append("; ERROR: output attempted on file channel not opened for write\n");
            return true;
        }

        int streamSlot = allocateNewLocal.apply("filePrintStream");
        activeOutput.append("new java/io/PrintStream\n")
                .append("dup\n")
                .append("new java/io/FileOutputStream\n")
                .append("dup\n")
                .append("ldc ").append(binding.filenameLiteral()).append("\n")
                .append("iconst_1\n")
                .append("invokespecial java/io/FileOutputStream/<init>(Ljava/lang/String;Z)V\n")
                .append("invokespecial java/io/PrintStream/<init>(Ljava/io/OutputStream;)V\n")
                .append("astore ").append(streamSlot).append("\n")
                .append("aload ").append(streamSlot).append("\n")
                .append(valueCode)
                .append("invokevirtual java/io/PrintStream/print(").append(printDescriptor).append(")V\n")
                .append("aload ").append(streamSlot).append("\n")
                .append("invokevirtual java/io/PrintStream/close()V\n");
        return true;
    }

    private boolean emitStringOrFileOutput(PerseusParser.ArgContext channelArg, String printDescriptor, String valueCode,
            StringBuilder activeOutput, Function<String, String> lookupVarType, Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName, Function<String, Integer> allocateNewLocal) {
        Integer channel = getConstantChannelValue(channelArg);
        if (channel == null) {
            return false;
        }
        if (constantStringChannels.containsKey(channel)) {
            String appendValueCode = valueCode;
            if ("I".equals(printDescriptor)) {
                appendValueCode += "invokestatic java/lang/String/valueOf(I)Ljava/lang/String;\n";
            } else if ("D".equals(printDescriptor)) {
                appendValueCode += "invokestatic java/lang/String/valueOf(D)Ljava/lang/String;\n";
            }
            return emitStringChannelWrite(channel, appendValueCode, activeOutput, lookupVarType, currentLocalIndex,
                    staticFieldName);
        }
        return false;
    }

    private boolean emitFileChannelInstring(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<String, Integer> allocateNewLocal, Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable, Map<String, String> mainSymbolTable,
            BiFunction<String, String, String> staticFieldName) {
        if (args.size() < 2) {
            activeOutput.append("; ERROR: instring requires channel and variable\n");
            return true;
        }

        Integer channel = getConstantChannelValue(args.get(0));
        if (channel != null && channel < 2) {
            return false;
        }
        if (channel != null && constantStringChannels.containsKey(channel)) {
            return false;
        }

        PerseusParser.ExprContext varExpr = args.get(1).expr();
        if (!(varExpr instanceof PerseusParser.VarExprContext)) {
            activeOutput.append("; ERROR: instring requires a variable as second argument\n");
            return true;
        }

        String varName = ((PerseusParser.VarExprContext) varExpr).identifier().getText();
        Integer varSlot = currentLocalIndex.get(varName);
        String varType = currentSymbolTable.get(varName);
        if (varType == null && mainSymbolTable != null) {
            varType = mainSymbolTable.get(varName);
        }

        appendChannelValue(args.get(0), activeOutput, generateExpr);
        activeOutput.append("invokestatic perseus/io/Channels/instring(I)Ljava/lang/String;\n");

        if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:")
                && !varType.startsWith("thunk:")) {
            activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                    .append("/").append(staticFieldName.apply(varName, varType)).append(" Ljava/lang/String;\n");
        } else if (varSlot == null) {
            activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
        } else {
            activeOutput.append("astore ").append(varSlot).append("\n");
        }
        return true;
    }
}
