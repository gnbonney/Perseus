comment Compiled standard-environment slice for text output helpers.;
namespace perseus.io;

external java class java.io.PrintStream;
external java class java.lang.Object;
external java class java.lang.IllegalArgumentException;
external java static(gnb.perseus.runtime.Channels)
    procedure outString(integer channel; string text) as runtime_outstring;
external java static(gnb.perseus.runtime.TextFormatSupport)
    string procedure formatOne(string formatSpec; ref(Object) objectValue) as runtime_formatone;
external java static(java.lang.Double)
    string procedure toString(real number) as real_toString;
external java static(java.lang.Integer)
    string procedure toString(integer number) as integer_toString;
external java static(java.lang.Integer)
    integer procedure parseInt(string text) as integer_parseInt;
external java static(java.lang.String)
    string procedure valueOf(ref(Object) objectValue) as string_valueOf;

external java static(java.lang.System)
    ref(PrintStream) out as stdout;
external java static(java.lang.System)
    ref(PrintStream) err as stderr;

boolean procedure formatdelimiter(ch);
    value ch;
    integer ch;
begin
    formatdelimiter := ch = 44 | ch = 32 | ch = 9 | ch = 10 | ch = 13
end;

boolean procedure formatdigit(ch);
    value ch;
    integer ch;
begin
    formatdigit := ch >= 48 & ch <= 57
end;

boolean procedure formatdigits(text);
    value text;
    string text;
begin
    integer i;

    if text.length() = 0 then
        formatdigits := false
    else begin
        formatdigits := true;
        for i := 1 step 1 until text.length() do
            if ~formatdigit(text.charAt(i - 1)) then
                formatdigits := false
    end
end;

boolean procedure formatxspec(token);
    value token;
    string token;
begin
    integer last;

    if token.length() < 2 then
        formatxspec := false
    else begin
        last := token.charAt(token.length() - 1);
        if last = 88 | last = 120 then
            formatxspec := formatdigits(token.substring(0, token.length() - 1))
        else
            formatxspec := false
    end
end;

boolean procedure formatvaluespec(token);
    value token;
    string token;
begin
    if token.length() = 1 then
        formatvaluespec := token.charAt(0) <> 47
    else
        formatvaluespec := ~formatxspec(token)
end;

string procedure repeatspaces(count);
    value count;
    integer count;
begin
    if count <= 0 then
        repeatspaces := ""
    else
        repeatspaces := concat(" ", repeatspaces(count - 1))
end;

procedure signalformatmismatch;
begin
    signal new IllegalArgumentException("format/argument count mismatch")
end;

procedure signalunsupportedformat(token);
    value token;
    string token;
begin
    string message;

    message := "Unsupported format token: ";
    signal new IllegalArgumentException(message.concat(token))
end;

string procedure formatstringtoken(token, text);
    value token, text;
    string token;
    string text;
begin
    integer width;
    string padding;

    if token.length() = 1 then
        formatstringtoken := text
    else begin
        if ~formatdigits(token.substring(1)) then
            signalunsupportedformat(token);
        width := integer_parseInt(token.substring(1));
        if text.length() < width then begin
            padding := repeatspaces(width - text.length());
            formatstringtoken := padding.concat(text)
        end else
            formatstringtoken := text
    end
end;

string procedure formatvalues(formatLiteral, values, valueCount);
    value formatLiteral, values, valueCount;
    string formatLiteral;
    ref(Object) array values;
    integer valueCount;
begin
    integer i, len, tokenStart, valueIndex, kind;
    boolean previousValueSpec, currentValueSpec;
    string token, result;

    len := formatLiteral.length();
    valueIndex := 1;
    previousValueSpec := false;
    result := "";
    i := 1;

scan:
    if i > len then
        goto done;

    if formatdelimiter(formatLiteral.charAt(i - 1)) then begin
        i := i + 1;
        goto scan
    end;

    tokenStart := i;
tokencollect:
    if i > len then
        goto tokenready;
    if formatdelimiter(formatLiteral.charAt(i - 1)) then
        goto tokenready;
    i := i + 1;
    goto tokencollect;
tokenready:
    token := formatLiteral.substring(tokenStart - 1, i - 1);
    currentValueSpec := formatvaluespec(token);

    if previousValueSpec & currentValueSpec then
        result := result.concat(" ");

    if token.length() = 1 then begin
        kind := token.charAt(0);
        if kind = 47 then
            result := result.concat("\n")
        else if kind = 65 | kind = 97 then begin
            if valueIndex > valueCount then
                signalformatmismatch;
            result := result.concat(formatstringtoken(token, string_valueOf(values[valueIndex])));
            valueIndex := valueIndex + 1
        end else begin
            if valueIndex > valueCount then
                signalformatmismatch;
            result := result.concat(runtime_formatone(token, values[valueIndex]));
            valueIndex := valueIndex + 1
        end
    end else if formatxspec(token) then
        result := result.concat(repeatspaces(integer_parseInt(token.substring(0, token.length() - 1))))
    else if token.charAt(0) = 65 | token.charAt(0) = 97 then begin
        if valueIndex > valueCount then
            signalformatmismatch;
        result := result.concat(formatstringtoken(token, string_valueOf(values[valueIndex])));
        valueIndex := valueIndex + 1
    end else begin
        if valueIndex > valueCount then
            signalformatmismatch;
        result := result.concat(runtime_formatone(token, values[valueIndex]));
        valueIndex := valueIndex + 1
    end;

    previousValueSpec := currentValueSpec;
    goto scan;
done:
    if valueIndex <> valueCount + 1 then
        signalformatmismatch;
    formatvalues := result
end;

procedure outstring(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    if channel = 0 then stderr.print(text)
    else if channel = 1 then stdout.print(text)
    else runtime_outstring(channel, text)
end;

procedure outreal(channel, number);
    value channel, number;
    integer channel;
    real number;
begin
    if channel = 0 then stderr.print(number)
    else if channel = 1 then stdout.print(number)
    else runtime_outstring(channel, real_toString(number))
end;

procedure outinteger(channel, number);
    value channel, number;
    integer channel, number;
begin
    if channel = 0 then stderr.print(number)
    else if channel = 1 then stdout.print(number)
    else runtime_outstring(channel, integer_toString(number))
end;

procedure outterminator(channel);
    value channel;
    integer channel;
begin
    if channel = 0 then stderr.print(" ")
    else if channel = 1 then stdout.print(" ")
    else runtime_outstring(channel, " ")
end;

procedure outchar(channel, text, position);
    value channel, text, position;
    integer channel, position;
    string text;
begin
    outstring(channel, text.substring(position, position + 1))
end
