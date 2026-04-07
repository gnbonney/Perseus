comment Compiled standard-environment slice for text output helpers.;
namespace perseus.io;

external java class java.io.PrintStream;
external java class java.lang.Object;
external java class java.lang.IllegalArgumentException;
external java static(java.lang.Double)
    string procedure toString(real number) as real_toString;
external java static(java.lang.Integer)
    string procedure toString(integer number) as integer_toString;
external java static(java.lang.Integer)
    integer procedure parseInt(string text) as integer_parseInt;
external java static(java.lang.Double)
    real procedure parseDouble(string text) as real_parseDouble;
external java static(java.lang.String)
    string procedure valueOf(ref(Object) objectValue) as string_valueOf;
external java static(java.lang.Math)
    real procedure abs(real number) as math_abs;
external java static(java.lang.Math)
    real procedure floor(real number) as math_floor;

external java static(java.lang.System)
    ref(PrintStream) out as stdout;
external java static(java.lang.System)
    ref(PrintStream) err as stderr;
external(perseus.io.Channels)
    procedure outstring(integer channel; string text) as channel_outstring;

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

string procedure repeatzeros(count);
    value count;
    integer count;
begin
    if count <= 0 then
        repeatzeros := ""
    else
        repeatzeros := concat("0", repeatzeros(count - 1))
end;

string procedure padleft(text, width);
    value text, width;
    string text;
    integer width;
begin
    if text.length() < width then
        padleft := concat(repeatspaces(width - text.length()), text)
    else
        padleft := text
end;

integer procedure power10(count);
    value count;
    integer count;
begin
    integer i, result;

    if count <= 0 then
        power10 := 1
    else begin
        result := 1;
        for i := 1 step 1 until count do
            result := result * 10;
        power10 := result
    end
end;

integer procedure roundpositive(number);
    value number;
    real number;
begin
    roundpositive := math_floor(number + 0.5)
end;

string procedure formatfixedwidth(width, decimals, number);
    value width, decimals, number;
    integer width, decimals;
    real number;
begin
    integer scale, scaled, intpart, fracpart;
    string sign, result, fractext;

    scale := power10(decimals);
    scaled := roundpositive(math_abs(number) * scale);
    intpart := scaled div scale;
    fracpart := scaled - intpart * scale;

    if number < 0.0 then
        sign := "-"
    else
        sign := "";

    result := concat(sign, integer_toString(intpart));
    if decimals > 0 then begin
        fractext := integer_toString(fracpart);
        if fractext.length() < decimals then
            fractext := concat(repeatzeros(decimals - fractext.length()), fractext);
        result := concat(result, ".");
        result := concat(result, fractext)
    end;

    formatfixedwidth := padleft(result, width)
end;

string procedure formatintegertoken(token, text);
    value token, text;
    string token, text;
begin
    integer width, dot, mindigits, number;
    string sign, digits, result;

    dot := token.indexOf(".");
    if dot < 0 then begin
        if ~formatdigits(token.substring(1)) then
            signalunsupportedformat(token);
        width := integer_parseInt(token.substring(1));
        mindigits := 0
    end else begin
        if ~formatdigits(token.substring(1, dot)) then
            signalunsupportedformat(token);
        if ~formatdigits(token.substring(dot + 1)) then
            signalunsupportedformat(token);
        width := integer_parseInt(token.substring(1, dot));
        mindigits := integer_parseInt(token.substring(dot + 1))
    end;

    number := integer_parseInt(text);
    if number < 0 then begin
        sign := "-";
        digits := integer_toString(-number)
    end else begin
        sign := "";
        digits := integer_toString(number)
    end;

    if digits.length() < mindigits then
        digits := concat(repeatzeros(mindigits - digits.length()), digits);
    result := concat(sign, digits);
    formatintegertoken := padleft(result, width)
end;

string procedure formatlogicaltoken(token, text);
    value token, text;
    string token, text;
begin
    integer width;

    if ~formatdigits(token.substring(1)) then
        signalunsupportedformat(token);
    width := integer_parseInt(token.substring(1));
    formatlogicaltoken := padleft(text, width)
end;

string procedure formatscientifictoken(token, text);
    value token, text;
    string token, text;
begin
    integer width, decimals, dot, exponent, scale, mantissascaled, intpart, fracpart, absExponent;
    real number, magnitude;
    string sign, mantissatext, fractext, exponenttext, result;

    dot := token.indexOf(".");
    if dot < 0 then
        signalunsupportedformat(token);
    if ~formatdigits(token.substring(1, dot)) then
        signalunsupportedformat(token);
    if ~formatdigits(token.substring(dot + 1)) then
        signalunsupportedformat(token);

    width := integer_parseInt(token.substring(1, dot));
    decimals := integer_parseInt(token.substring(dot + 1));
    number := real_parseDouble(text);
    scale := power10(decimals);

    if number < 0.0 then
        sign := "-"
    else
        sign := "";

    magnitude := math_abs(number);
    exponent := 0;
    if magnitude <> 0.0 then begin
normalizehigh:
        if magnitude < 10.0 then
            goto normalizelow;
        magnitude := magnitude / 10;
        exponent := exponent + 1;
        goto normalizehigh;
normalizelow:
        if magnitude >= 1.0 then
            goto normalizedone;
        magnitude := magnitude * 10;
        exponent := exponent - 1;
        goto normalizelow;
normalizedone:
    end;

    mantissascaled := roundpositive(magnitude * scale);
    if mantissascaled >= 10 * scale then begin
        mantissascaled := scale;
        exponent := exponent + 1
    end;

    intpart := mantissascaled div scale;
    fracpart := mantissascaled - intpart * scale;
    mantissatext := concat(sign, integer_toString(intpart));
    if decimals > 0 then begin
        fractext := integer_toString(fracpart);
        if fractext.length() < decimals then
            fractext := concat(repeatzeros(decimals - fractext.length()), fractext);
        mantissatext := concat(mantissatext, ".");
        mantissatext := concat(mantissatext, fractext)
    end;

    if exponent < 0 then begin
        absExponent := -exponent;
        exponenttext := "e-"
    end else begin
        absExponent := exponent;
        exponenttext := "e+"
    end;
    exponenttext := concat(exponenttext, integer_toString(absExponent));
    if absExponent < 10 then
        exponenttext := concat(exponenttext.substring(0, 2), concat("0", exponenttext.substring(2)));

    result := concat(mantissatext, exponenttext);
    formatscientifictoken := padleft(result, width)
end;

string procedure formatrealtoken(token, text);
    value token, text;
    string token, text;
begin
    integer dot, width, decimals, kind;
    real number;

    dot := token.indexOf(".");
    if dot < 0 then
        signalunsupportedformat(token);
    if ~formatdigits(token.substring(1, dot)) then
        signalunsupportedformat(token);
    if ~formatdigits(token.substring(dot + 1)) then
        signalunsupportedformat(token);

    width := integer_parseInt(token.substring(1, dot));
    decimals := integer_parseInt(token.substring(dot + 1));
    kind := token.charAt(0);
    number := real_parseDouble(text);

    if kind = 70 | kind = 102 then
        formatrealtoken := formatfixedwidth(width, decimals, number)
    else if kind = 69 | kind = 101 then
        formatrealtoken := formatscientifictoken(token, text)
    else
        signalunsupportedformat(token)
end;

string procedure formatvaluetoken(token, text);
    value token, text;
    string token, text;
begin
    integer kind;
    kind := token.charAt(0);

    if kind = 73 | kind = 105 then
        formatvaluetoken := formatintegertoken(token, text)
    else if kind = 70 | kind = 102 | kind = 69 | kind = 101 then
        formatvaluetoken := formatrealtoken(token, text)
    else if kind = 76 | kind = 108 then
        formatvaluetoken := formatlogicaltoken(token, text)
    else
        signalunsupportedformat(token)
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
            result := result.concat(formatvaluetoken(token, string_valueOf(values[valueIndex])));
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
        result := result.concat(formatvaluetoken(token, string_valueOf(values[valueIndex])));
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
    else channel_outstring(channel, text)
end;

procedure outreal(channel, number);
    value channel, number;
    integer channel;
    real number;
begin
    if channel = 0 then stderr.print(number)
    else if channel = 1 then stdout.print(number)
    else channel_outstring(channel, real_toString(number))
end;

procedure outinteger(channel, number);
    value channel, number;
    integer channel, number;
begin
    if channel = 0 then stderr.print(number)
    else if channel = 1 then stdout.print(number)
    else channel_outstring(channel, integer_toString(number))
end;

procedure outterminator(channel);
    value channel;
    integer channel;
begin
    if channel = 0 then stderr.print(" ")
    else if channel = 1 then stdout.print(" ")
    else channel_outstring(channel, " ")
end;

procedure outchar(channel, text, position);
    value channel, text, position;
    integer channel, position;
    string text;
begin
    outstring(channel, text.substring(position, position + 1))
end
