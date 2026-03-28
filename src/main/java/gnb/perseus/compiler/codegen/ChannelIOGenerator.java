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
            case "openfile" -> emitOpenFile(args, activeOutput, allocateNewLocal);
            case "openstring" -> emitOpenString(args, activeOutput);
            case "closefile" -> emitCloseFile(args, activeOutput);
            case "outstring" -> emitOutString(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outreal" -> emitOutReal(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outinteger" -> emitOutInteger(args, activeOutput, generateExpr, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outterminator" -> emitOutTerminator(args, activeOutput, getChannelStream, lookupVarType,
                    currentLocalIndex, staticFieldName, allocateNewLocal);
            case "outformat" -> emitOutformat(args, activeOutput, generateExpr, exprTypeResolver, getChannelStream,
                    lookupVarType, currentLocalIndex, staticFieldName, allocateNewLocal);
            case "informat" -> emitInformat(args, activeOutput, allocateNewLocal, currentLocalIndex, currentSymbolTable,
                    mainSymbolTable, staticFieldName);
            case "instring" -> emitInstring(args, activeOutput, allocateNewLocal, currentLocalIndex,
                    currentSymbolTable, mainSymbolTable, staticFieldName);
            default -> false;
        };
    }

    private record FormatSpec(char kind, int width, int precision) {}

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

    private String unquote(String literal) {
        if (literal == null || literal.length() < 2 || literal.charAt(0) != '"' || literal.charAt(literal.length() - 1) != '"') {
            return literal;
        }
        return literal.substring(1, literal.length() - 1).replace("\\\"", "\"").replace("\\n", "\n");
    }

    private List<FormatSpec> parseFormatSpecs(String formatLiteral) {
        String raw = unquote(formatLiteral);
        List<FormatSpec> specs = new ArrayList<>();
        for (String token : raw.split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            char kind = Character.toUpperCase(token.charAt(0));
            if (kind == 'I' || kind == 'A') {
                int width = Integer.parseInt(token.substring(1));
                specs.add(new FormatSpec(kind, width, -1));
            } else if (kind == 'F') {
                int dot = token.indexOf('.');
                int width = Integer.parseInt(token.substring(1, dot));
                int precision = Integer.parseInt(token.substring(dot + 1));
                specs.add(new FormatSpec(kind, width, precision));
            } else {
                throw new IllegalArgumentException("Unsupported format token: " + token);
            }
        }
        return specs;
    }

    private String toJavaFormatLiteral(List<FormatSpec> specs) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                sb.append(" ");
            }
            FormatSpec spec = specs.get(i);
            sb.append("%").append(spec.width());
            if (spec.kind() == 'F') {
                sb.append(".").append(spec.precision()).append("f");
            } else if (spec.kind() == 'I') {
                sb.append("d");
            } else {
                sb.append("s");
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private String generateFormattedString(List<FormatSpec> specs, List<PerseusParser.ArgContext> valueArgs,
            Function<PerseusParser.ExprContext, String> generateExpr,
            Function<PerseusParser.ExprContext, String> exprTypeResolver,
            StringBuilder activeOutput) {
        if (specs.size() != valueArgs.size()) {
            activeOutput.append("; ERROR: format/argument count mismatch\n");
            return "ldc \"\"\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ldc ").append(toJavaFormatLiteral(specs)).append("\n");
        sb.append("ldc ").append(specs.size()).append("\n");
        sb.append("anewarray java/lang/Object\n");
        for (int i = 0; i < specs.size(); i++) {
            FormatSpec spec = specs.get(i);
            PerseusParser.ExprContext expr = valueArgs.get(i).expr();
            String exprType = expr != null ? exprTypeResolver.apply(expr) : "integer";
            sb.append("dup\n");
            sb.append("ldc ").append(i).append("\n");
            sb.append(generateExpr.apply(expr));
            if (spec.kind() == 'F') {
                if (!"real".equals(exprType)) {
                    sb.append("i2d\n");
                }
                sb.append("invokestatic java/lang/Double/valueOf(D)Ljava/lang/Double;\n");
            } else if (spec.kind() == 'I') {
                if ("real".equals(exprType)) {
                    sb.append("d2i\n");
                }
                sb.append("invokestatic java/lang/Integer/valueOf(I)Ljava/lang/Integer;\n");
            }
            sb.append("aastore\n");
        }
        sb.append("invokestatic java/lang/String/format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;\n");
        return sb.toString();
    }

    private boolean emitOpenFile(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<String, Integer> allocateNewLocal) {
        if (args.size() < 3) {
            activeOutput.append("; ERROR: openfile requires channel, filename, and mode\n");
            return true;
        }
        Integer channel = getConstantChannelValue(args.get(0));
        String filenameLiteral = getStringLiteralText(args.get(1));
        String modeLiteral = getStringLiteralText(args.get(2));
        if (channel == null || filenameLiteral == null || modeLiteral == null) {
            activeOutput.append("; ERROR: openfile currently requires a constant channel and string literals for filename and mode\n");
            return true;
        }

        String mode = switch (modeLiteral) {
            case "\"w\"" -> "w";
            case "\"r\"" -> "r";
            default -> null;
        };
        if (mode == null) {
            activeOutput.append("; ERROR: openfile mode must currently be \"r\" or \"w\"\n");
            return true;
        }

        constantFileChannels.put(channel, new FileChannelBinding(filenameLiteral, mode));
        constantStringChannels.remove(channel);

        int streamSlot = allocateNewLocal.apply("fileChannel");
        if ("w".equals(mode)) {
            activeOutput.append("new java/io/FileOutputStream\n")
                    .append("dup\n")
                    .append("ldc ").append(filenameLiteral).append("\n")
                    .append("iconst_0\n")
                    .append("invokespecial java/io/FileOutputStream/<init>(Ljava/lang/String;Z)V\n")
                    .append("astore ").append(streamSlot).append("\n")
                    .append("aload ").append(streamSlot).append("\n")
                    .append("invokevirtual java/io/FileOutputStream/close()V\n");
        } else {
            activeOutput.append("new java/io/FileInputStream\n")
                    .append("dup\n")
                    .append("ldc ").append(filenameLiteral).append("\n")
                    .append("invokespecial java/io/FileInputStream/<init>(Ljava/lang/String;)V\n")
                    .append("astore ").append(streamSlot).append("\n")
                    .append("aload ").append(streamSlot).append("\n")
                    .append("invokevirtual java/io/FileInputStream/close()V\n");
        }
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

    private boolean emitCloseFile(List<PerseusParser.ArgContext> args, StringBuilder activeOutput) {
        if (args.isEmpty()) {
            activeOutput.append("; ERROR: closefile requires a channel\n");
            return true;
        }
        Integer channel = getConstantChannelValue(args.get(0));
        if (channel != null) {
            constantFileChannels.remove(channel);
            constantStringChannels.remove(channel);
        } else {
            activeOutput.append("; WARNING: closefile currently requires a constant channel to clear channel state\n");
        }
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
            String stream = getChannelStream.apply(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                    .append(generateExpr.apply(args.get(args.size() - 1).expr()))
                    .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
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
            String stream = getChannelStream.apply(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                    .append(generateExpr.apply(args.get(args.size() - 1).expr()))
                    .append("invokevirtual java/io/PrintStream/print(D)V\n");
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
            String stream = getChannelStream.apply(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                    .append(generateExpr.apply(args.get(args.size() - 1).expr()))
                    .append("invokevirtual java/io/PrintStream/print(I)V\n");
        }
        return true;
    }

    private boolean emitOutTerminator(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<PerseusParser.ArgContext, String> getChannelStream,
            Function<String, String> lookupVarType,
            Map<String, Integer> currentLocalIndex,
            BiFunction<String, String, String> staticFieldName,
            Function<String, Integer> allocateNewLocal) {
        PerseusParser.ArgContext channelArg = args.size() > 0 ? args.get(0) : null;
        if (!emitStringOrFileOutput(channelArg, "Ljava/lang/String;", "ldc \" \"\n", activeOutput, lookupVarType,
                currentLocalIndex, staticFieldName, allocateNewLocal)) {
            String stream = getChannelStream.apply(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                    .append("ldc \" \"\n")
                    .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        }
        return true;
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
        String formatLiteral = getStringLiteralText(args.get(1));
        if (formatLiteral == null) {
            activeOutput.append("; ERROR: outformat currently requires a string-literal format\n");
            return true;
        }
        List<FormatSpec> specs = parseFormatSpecs(formatLiteral);
        String formattedValue = generateFormattedString(specs, args.subList(2, args.size()), generateExpr, exprTypeResolver,
                activeOutput);
        PerseusParser.ArgContext channelArg = args.get(0);
        if (!emitStringOrFileOutput(channelArg, "Ljava/lang/String;", formattedValue, activeOutput, lookupVarType,
                currentLocalIndex, staticFieldName, allocateNewLocal)) {
            String stream = getChannelStream.apply(channelArg);
            activeOutput.append("getstatic ").append(stream).append(" Ljava/io/PrintStream;\n")
                    .append(formattedValue)
                    .append("invokevirtual java/io/PrintStream/print(Ljava/lang/String;)V\n");
        }
        return true;
    }

    private boolean emitInstring(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<String, Integer> allocateNewLocal,
            Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable,
            Map<String, String> mainSymbolTable,
            BiFunction<String, String, String> staticFieldName) {
        if (!emitFileChannelInstring(args, activeOutput, allocateNewLocal, currentLocalIndex, currentSymbolTable,
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
            Function<String, Integer> allocateNewLocal,
            Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable,
            Map<String, String> mainSymbolTable,
            BiFunction<String, String, String> staticFieldName) {
        if (args.size() < 2) {
            activeOutput.append("; ERROR: informat requires at least a channel and format string\n");
            return true;
        }
        String formatLiteral = getStringLiteralText(args.get(1));
        if (formatLiteral == null) {
            activeOutput.append("; ERROR: informat currently requires a string-literal format\n");
            return true;
        }
        List<FormatSpec> specs = parseFormatSpecs(formatLiteral);
        if (specs.size() != args.size() - 2) {
            activeOutput.append("; ERROR: format/argument count mismatch\n");
            return true;
        }

        Integer channel = getConstantChannelValue(args.get(0));
        String scannerLoad;
        Integer scannerSlot = null;
        if (channel != null && constantFileChannels.containsKey(channel) && "r".equals(constantFileChannels.get(channel).mode())) {
            scannerSlot = allocateNewLocal.apply("formatScanner");
            FileChannelBinding binding = constantFileChannels.get(channel);
            activeOutput.append("new java/util/Scanner\n")
                    .append("dup\n")
                    .append("new java/io/FileInputStream\n")
                    .append("dup\n")
                    .append("ldc ").append(binding.filenameLiteral()).append("\n")
                    .append("invokespecial java/io/FileInputStream/<init>(Ljava/lang/String;)V\n")
                    .append("invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V\n")
                    .append("astore ").append(scannerSlot).append("\n");
            scannerLoad = "aload " + scannerSlot + "\n";
        } else {
            scannerLoad = "getstatic " + packageName + "/" + className + "/__scanner Ljava/util/Scanner;\n";
        }

        for (int i = 0; i < specs.size(); i++) {
            FormatSpec spec = specs.get(i);
            PerseusParser.ArgContext arg = args.get(i + 2);
            String varName = getVariableName(arg);
            if (varName == null) {
                activeOutput.append("; ERROR: informat requires variables after the format string\n");
                continue;
            }
            Integer varSlot = currentLocalIndex.get(varName);
            String varType = currentSymbolTable.get(varName);
            if (varType == null && mainSymbolTable != null) {
                varType = mainSymbolTable.get(varName);
            }

            activeOutput.append(scannerLoad);
            switch (spec.kind()) {
                case 'I' -> activeOutput.append("invokevirtual java/util/Scanner/nextInt()I\n");
                case 'F' -> activeOutput.append("invokevirtual java/util/Scanner/nextDouble()D\n");
                case 'A' -> activeOutput.append("invokevirtual java/util/Scanner/next()Ljava/lang/String;\n");
                default -> activeOutput.append("; ERROR: unsupported informat specifier\n");
            }

            if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:")
                    && !varType.startsWith("thunk:")) {
                String desc = spec.kind() == 'F' ? "D" : spec.kind() == 'A' ? "Ljava/lang/String;" : "I";
                activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                        .append("/").append(staticFieldName.apply(varName, varType)).append(" ").append(desc).append("\n");
            } else if (varSlot == null) {
                activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
            } else {
                String store = spec.kind() == 'F' ? "dstore " : spec.kind() == 'A' ? "astore " : "istore ";
                activeOutput.append(store).append(varSlot).append("\n");
            }
        }

        if (scannerSlot != null) {
            activeOutput.append("aload ").append(scannerSlot).append("\n")
                    .append("invokevirtual java/util/Scanner/close()V\n");
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
        return emitFileChannelPrint(channel, printDescriptor, valueCode, activeOutput, allocateNewLocal);
    }

    private boolean emitFileChannelInstring(List<PerseusParser.ArgContext> args, StringBuilder activeOutput,
            Function<String, Integer> allocateNewLocal, Map<String, Integer> currentLocalIndex,
            Map<String, String> currentSymbolTable, Map<String, String> mainSymbolTable,
            BiFunction<String, String, String> staticFieldName) {
        if (args.size() < 2) {
            activeOutput.append("; ERROR: instring requires channel and variable\n");
            return true;
        }
        Integer channel = getConstantChannelValue(args.get(0));
        if (channel == null) {
            return false;
        }
        FileChannelBinding binding = constantFileChannels.get(channel);
        if (binding == null) {
            return false;
        }
        if (!"r".equals(binding.mode())) {
            activeOutput.append("; ERROR: instring attempted on file channel not opened for read\n");
            return true;
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

        int scannerSlot = allocateNewLocal.apply("fileScanner");
        activeOutput.append("new java/util/Scanner\n")
                .append("dup\n")
                .append("new java/io/FileInputStream\n")
                .append("dup\n")
                .append("ldc ").append(binding.filenameLiteral()).append("\n")
                .append("invokespecial java/io/FileInputStream/<init>(Ljava/lang/String;)V\n")
                .append("invokespecial java/util/Scanner/<init>(Ljava/io/InputStream;)V\n")
                .append("astore ").append(scannerSlot).append("\n")
                .append("aload ").append(scannerSlot).append("\n")
                .append("invokevirtual java/util/Scanner/nextLine()Ljava/lang/String;\n");

        if (varSlot == null && varType != null && !varType.endsWith("[]") && !varType.startsWith("procedure:")
                && !varType.startsWith("thunk:")) {
            activeOutput.append("putstatic ").append(packageName).append("/").append(className)
                    .append("/").append(staticFieldName.apply(varName, varType)).append(" Ljava/lang/String;\n");
        } else if (varSlot == null) {
            activeOutput.append("; ERROR: undefined variable ").append(varName).append("\n");
        } else {
            activeOutput.append("astore ").append(varSlot).append("\n");
        }
        activeOutput.append("aload ").append(scannerSlot).append("\n")
                .append("invokevirtual java/util/Scanner/close()V\n");
        return true;
    }
}
