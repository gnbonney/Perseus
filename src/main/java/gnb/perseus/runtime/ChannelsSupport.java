package gnb.perseus.runtime;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChannelsSupport {
    private static final Map<Integer, BufferedReader> READ_CHANNELS = new ConcurrentHashMap<>();
    private static final Map<Integer, BufferedWriter> WRITE_CHANNELS = new ConcurrentHashMap<>();

    private ChannelsSupport() {
    }

    public static void openFile(int channel, String filename, String mode) {
        requireDynamicChannel(channel);
        closeFile(channel);
        try {
            Path path = Path.of(filename);
            switch (mode) {
                case "r" -> READ_CHANNELS.put(channel, Files.newBufferedReader(path, StandardCharsets.UTF_8));
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
        BufferedReader reader = READ_CHANNELS.remove(channel);
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

    public static void outReal(int channel, double number) {
        outString(channel, Double.toString(number));
    }

    public static void outInteger(int channel, int number) {
        outString(channel, Integer.toString(number));
    }

    public static void outTerminator(int channel) {
        outString(channel, " ");
    }

    public static String inString(int channel) {
        BufferedReader reader = requireReadChannel(channel);
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("End of file on channel " + channel);
            }
            return line;
        } catch (IOException e) {
            throw rethrowUnchecked(e);
        }
    }

    private static RuntimeException rethrowUnchecked(Throwable throwable) {
        ChannelsSupport.<RuntimeException>sneakyThrow(throwable);
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

    private static BufferedReader requireReadChannel(int channel) {
        requireDynamicChannel(channel);
        BufferedReader reader = READ_CHANNELS.get(channel);
        if (reader != null) {
            return reader;
        }
        if (WRITE_CHANNELS.containsKey(channel)) {
            throw new IllegalStateException("Channel " + channel + " is not open for read");
        }
        throw new IllegalStateException("Channel " + channel + " is not open");
    }
}
