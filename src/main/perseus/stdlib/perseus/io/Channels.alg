comment Standard environment channel support.;
namespace perseus.io;

external java class java.io.BufferedReader;
external java class java.io.BufferedWriter;
external java class java.io.EOFException;
external java class java.io.FileInputStream;
external java class java.io.FileOutputStream;
external java class java.io.InputStream;
external java class java.io.InputStreamReader;
external java class java.io.OutputStreamWriter;
external java class gnb.perseus.compiler.Thunk;
external java class java.lang.IllegalArgumentException;
external java class java.lang.IllegalStateException;
external java class java.lang.Integer;
external java class java.lang.StringBuilder;
external java class java.util.HashMap;
external java static(java.lang.Integer)
    string procedure toString(integer number) as integer_toString;
external java static(java.lang.Integer)
    ref(Integer) procedure valueOf(integer number) as integer_valueOf;
external java static(java.lang.String)
    string procedure valueOf(ref(Object) objectValue) as string_valueOf;
external java static(java.lang.System)
    ref(InputStream) in as stdin_stream;

ref(HashMap) readchannels;
ref(HashMap) writechannels;
ref(HashMap) stringchannels;
ref(HashMap) bufferedchars;
ref(BufferedReader) stdinreader;
integer stdinbufferedflag, stdinbufferedchar;

procedure ensurestate;
begin
    if readchannels = null then begin
        readchannels := new HashMap;
        writechannels := new HashMap;
        stringchannels := new HashMap;
        bufferedchars := new HashMap;
        stdinbufferedflag := 0
    end
end;

boolean procedure whitespace(ch);
    value ch;
    integer ch;
begin
    whitespace := ch = 32 or ch = 9 or ch = 10 or ch = 13
end;

ref(Integer) procedure channelkey(channel);
    value channel;
    integer channel;
begin
    channelkey := integer_valueOf(channel)
end;

string procedure channelmessage(prefix, channel, suffix);
    value prefix, channel, suffix;
    string prefix, suffix;
    integer channel;
begin
    string text;

    text := prefix.concat(integer_toString(channel));
    text := text.concat(suffix);
    channelmessage := text
end;

string procedure eofmessage(channel);
    value channel;
    integer channel;
begin
    eofmessage := channelmessage("End of file on channel ", channel, "")
end;

string procedure unsupportedmodemessage(mode);
    value mode;
    string mode;
begin
    string text;

    text := "Unsupported file mode: ";
    text := text.concat(mode);
    unsupportedmodemessage := text
end;

procedure ensuredynamicchannel(channel);
    value channel;
    integer channel;
begin
    if channel < 2 then
        signal new IllegalArgumentException(channelmessage("Channels 0 and 1 are reserved: ", channel, ""))
end;

procedure ensurestdin;
begin
    ensurestate;
    if stdinreader = null then begin
        stdinreader := new BufferedReader(new InputStreamReader(stdin_stream, "UTF-8"));
        stdinbufferedflag := 0
    end
end;

procedure unreadchar(channel, ch);
    value channel, ch;
    integer channel, ch;
begin
    ref(Integer) key;

    if channel < 2 then begin
        stdinbufferedflag := 1;
        stdinbufferedchar := ch
    end else begin
        ensurestate;
        ensuredynamicchannel(channel);
        key := channelkey(channel);
        bufferedchars.put(key, integer_valueOf(ch))
    end
end;

integer procedure readchar(channel);
    value channel;
    integer channel;
begin
    ref(Integer) key;
    ref(BufferedReader) reader;
    ref(Integer) boxedchar;

    if channel < 2 then begin
        ensurestdin;
        if stdinbufferedflag <> 0 then begin
            readchar := stdinbufferedchar;
            stdinbufferedflag := 0
        end else
            readchar := stdinreader.read
    end else begin
        ensurestate;
        ensuredynamicchannel(channel);
        key := channelkey(channel);
        reader := readchannels.get(key);
        if reader = null then
            if writechannels.containsKey(key) then
                signal new IllegalStateException(channelmessage("Channel ", channel, " is not open for read"))
            else if stringchannels.containsKey(key) then
                signal new IllegalStateException(channelmessage("Channel ", channel, " is not open for read"))
            else
                signal new IllegalStateException(channelmessage("Channel ", channel, " is not open"))
        else if bufferedchars.containsKey(key) then begin
            boxedchar := bufferedchars.remove(key);
            readchar := boxedchar.intValue()
        end else
            readchar := reader.read
    end
end;

procedure closefile(channel);
    value channel;
    integer channel;
begin
    ref(Integer) key;
    ref(BufferedReader) reader;
    ref(BufferedWriter) writer;
    ref(Thunk) stringtarget;

    ensurestate;
    ensuredynamicchannel(channel);
    key := channelkey(channel);
    reader := readchannels.remove(key);
    writer := writechannels.remove(key);
    stringtarget := stringchannels.remove(key);
    bufferedchars.remove(key);

    if reader <> null then
        reader.close;
    if writer <> null then
        writer.close
end;

procedure openfile(channel, filename, mode);
    value channel, filename, mode;
    integer channel;
    string filename, mode;
begin
    integer kind;
    ref(Integer) key;

    ensurestate;
    ensuredynamicchannel(channel);
    closefile(channel);

    if mode.length() <> 1 then
        signal new IllegalArgumentException(unsupportedmodemessage(mode));
    kind := mode.charAt(0);
    key := channelkey(channel);

    if kind = 114 then
        readchannels.put(key, new BufferedReader(
            new InputStreamReader(new FileInputStream(filename), "UTF-8")))
    else if kind = 119 then
        writechannels.put(key, new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(filename, false), "UTF-8")))
    else if kind = 97 then
        writechannels.put(key, new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(filename, true), "UTF-8")))
    else
        signal new IllegalArgumentException(unsupportedmodemessage(mode))
end;

procedure openstring(channel, target);
    value channel, target;
    integer channel;
    ref(Thunk) target;
begin
    ref(Integer) key;

    ensurestate;
    ensuredynamicchannel(channel);
    closefile(channel);
    key := channelkey(channel);
    stringchannels.put(key, target)
end;

procedure outstring(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    ref(Integer) key;
    ref(BufferedWriter) writer;
    ref(Thunk) stringtarget;
    ref(Object) currentobject;
    string current;

    ensurestate;
    ensuredynamicchannel(channel);
    key := channelkey(channel);
    stringtarget := stringchannels.get(key);
    if stringtarget <> null then begin
        currentobject := stringtarget.get();
        if currentobject = null then
            current := ""
        else
            current := string_valueOf(currentobject);
        stringtarget.set(current.concat(text));
        goto done
    end;
    writer := writechannels.get(key);
    if writer = null then
        if readchannels.containsKey(key) then
            signal new IllegalStateException(channelmessage("Channel ", channel, " is not open for write"))
        else if stringchannels.containsKey(key) then
            signal new IllegalStateException(channelmessage("Channel ", channel, " is not open for write"))
        else
            signal new IllegalStateException(channelmessage("Channel ", channel, " is not open"))
    else begin
        writer.write(text);
        writer.flush
    end;
done:
end;

string procedure intoken(channel);
    value channel;
    integer channel;
begin
    integer ch;
    ref(StringBuilder) token;

    token := new StringBuilder;
skip:
    ch := readchar(channel);
    if ch = -1 then
        signal new EOFException(eofmessage(channel));
    if whitespace(ch) then
        goto skip;

collect:
    token.appendCodePoint(ch);
    ch := readchar(channel);
    if ch = -1 then
        goto done;
    if whitespace(ch) then begin
        unreadchar(channel, ch);
        goto done
    end;
    goto collect;
done:
    intoken := token.toString()
end;

string procedure instring(channel);
    value channel;
    integer channel;
begin
    integer ch;
    ref(StringBuilder) line;
    string text;

    line := new StringBuilder;
scan:
    ch := readchar(channel);
    if ch = -1 then begin
        text := line.toString();
        if text.length() = 0 then
            signal new EOFException(eofmessage(channel))
        else
            goto done
    end;
    if ch = 10 then
        goto done;
    if ch = 13 then begin
        ch := readchar(channel);
        if ch <> 10 and ch <> -1 then
            unreadchar(channel, ch);
        goto done
    end;

    line.appendCodePoint(ch);
    goto scan;
done:
    instring := line.toString()
end
