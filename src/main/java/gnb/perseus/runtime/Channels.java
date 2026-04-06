package gnb.perseus.runtime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared runtime owner for dynamic channel state and channel-aware I/O helpers.
 */
public final class Channels {
    private static final int NO_BUFFERED_CHAR = -2;
    private static final Scanner STDIN_SCANNER = new Scanner(System.in);
    private static final Map<Integer, InputChannel> READ_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<Integer, BufferedWriter> WRITE_CHANNELS = new ConcurrentHashMap<>();

    private Channels() {
    }

    public static void openFile(int channel, String filename, String mode) {
        requireDynamicChannel(channel);
        closeFile(channel);
        try {
            Path path = Path.of(filename);
            switch (mode) {
                case "r" -> READ_CHANNELS.put(channel,
                        new InputChannel(Files.newBufferedReader(path, StandardCharsets.UTF_8)));
                case "w" -> WRITE_CHANNELS.put(channel, Files.newBufferedWriter(
                        path,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE));
                case "a" -> WRITE_CHANNELS.put(channel, Files.newBufferedWriter(
                        path,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE));
                default -> throw new IllegalArgumentException("Unsupported file mode: " + mode);
            }
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static void closeFile(int channel) {
        requireDynamicChannel(channel);
        InputChannel reader = READ_CHANNELS.remove(channel);
        BufferedWriter writer = WRITE_CHANNELS.remove(channel);
        try {
            if (reader != null) {
                reader.close();
            }
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static void outString(int channel, String text) {
        BufferedWriter writer = requireWriteChannel(channel);
        try {
            writer.write(text);
            writer.flush();
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static String inString(int channel) {
        InputChannel reader = requireReadChannel(channel);
        try {
            return reader.readLine(channel);
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static int ininteger(int channel) {
        if (channel >= 2) {
            return inInteger(channel);
        }
        return STDIN_SCANNER.nextInt();
    }

    public static int inInteger(int channel) {
        InputChannel reader = requireReadChannel(channel);
        try {
            return Integer.parseInt(reader.readToken(channel));
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static double inreal(int channel) {
        if (channel >= 2) {
            return inReal(channel);
        }
        return STDIN_SCANNER.nextDouble();
    }

    public static double inReal(int channel) {
        InputChannel reader = requireReadChannel(channel);
        try {
            return Double.parseDouble(reader.readToken(channel));
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static int inchar(int channel, String text) {
        if (channel >= 2) {
            return inChar(channel, text);
        }
        return text.indexOf(STDIN_SCANNER.next().charAt(0));
    }

    public static int inChar(int channel, String text) {
        InputChannel reader = requireReadChannel(channel);
        try {
            String token = reader.readToken(channel);
            return text.indexOf(token.charAt(0));
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    public static String inToken(int channel) {
        if (channel >= 2) {
            return readDynamicToken(channel);
        }
        return STDIN_SCANNER.next();
    }

    private static String readDynamicToken(int channel) {
        InputChannel reader = requireReadChannel(channel);
        try {
            return reader.readToken(channel);
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    private static RuntimeException rethrowUnchecked(Throwable throwable) {
        Channels.<RuntimeException>sneakyThrow(throwable);
        throw new AssertionError("unreachable");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static void requireDynamicChannel(int channel) {
        if (channel < 2) {
            throw new IllegalArgumentException("Channels 0 and 1 are reserved: " + channel);
        }
    }

    private static BufferedWriter requireWriteChannel(int channel) {
        requireDynamicChannel(channel);
        BufferedWriter writer = WRITE_CHANNELS.get(channel);
        if (writer != null) {
            return writer;
        }
        if (READ_CHANNELS.containsKey(channel)) {
            throw new IllegalStateException("Channel " + channel + " is not open for write");
        }
        throw new IllegalStateException("Channel " + channel + " is not open");
    }

    private static InputChannel requireReadChannel(int channel) {
        requireDynamicChannel(channel);
        InputChannel reader = READ_CHANNELS.get(channel);
        if (reader != null) {
            return reader;
        }
        if (WRITE_CHANNELS.containsKey(channel)) {
            throw new IllegalStateException("Channel " + channel + " is not open for read");
        }
        throw new IllegalStateException("Channel " + channel + " is not open");
    }

    static final class InputChannel implements AutoCloseable {
        private final BufferedReader reader;
        private int bufferedChar = NO_BUFFERED_CHAR;

        InputChannel(BufferedReader reader) {
            this.reader = reader;
        }

        String readToken(int channel) throws IOException {
            int ch;
            do {
                ch = readChar();
                if (ch == -1) {
                    throw eof(channel);
                }
            } while (Character.isWhitespace(ch));

            StringBuilder token = new StringBuilder();
            while (ch != -1 && !Character.isWhitespace(ch)) {
                token.append((char) ch);
                ch = readChar();
            }
            if (ch != -1) {
                unreadChar(ch);
            }
            return token.toString();
        }

        String readLine(int channel) throws IOException {
            StringBuilder line = new StringBuilder();
            while (true) {
                int ch = readChar();
                if (ch == -1) {
                    if (line.isEmpty()) {
                        throw eof(channel);
                    }
                    return line.toString();
                }
                if (ch == '\n') {
                    return line.toString();
                }
                if (ch == '\r') {
                    int next = readChar();
                    if (next != '\n' && next != -1) {
                        unreadChar(next);
                    }
                    return line.toString();
                }
                line.append((char) ch);
            }
        }

        private int readChar() throws IOException {
            if (bufferedChar != NO_BUFFERED_CHAR) {
                int ch = bufferedChar;
                bufferedChar = NO_BUFFERED_CHAR;
                return ch;
            }
            return reader.read();
        }

        private void unreadChar(int ch) {
            bufferedChar = ch;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    private static EOFException eof(int channel) {
        return new EOFException("End of file on channel " + channel);
    }
}
