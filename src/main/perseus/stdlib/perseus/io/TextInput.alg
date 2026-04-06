comment Compiled standard-environment slice for text input helpers.;
namespace perseus.io;

external java class java.lang.Object;
external java class java.lang.IllegalArgumentException;
external java static(gnb.perseus.runtime.Channels)
    string procedure inToken(integer channel) as runtime_intoken;
external java static(java.lang.Integer)
    integer procedure parseInt(string text) as integer_parseInt;
external java static(java.lang.Double)
    real procedure parseDouble(string text) as real_parseDouble;

boolean procedure informatdelimiter(ch);
    value ch;
    integer ch;
begin
    informatdelimiter := ch = 44 | ch = 32 | ch = 9 | ch = 10 | ch = 13
end;

boolean procedure informatdigit(ch);
    value ch;
    integer ch;
begin
    informatdigit := ch >= 48 & ch <= 57
end;

boolean procedure informatdigits(text);
    value text;
    string text;
begin
    integer i;

    if text.length() = 0 then
        informatdigits := false
    else begin
        informatdigits := true;
        for i := 1 step 1 until text.length() do
            if ~informatdigit(text.charAt(i - 1)) then
                informatdigits := false
    end
end;

procedure informatsignalunsupported(token);
    value token;
    string token;
begin
    signal new IllegalArgumentException(concat("Unsupported format token: ", token))
end;

procedure informatvalues(channel, formatLiteral, values);
    value channel, formatLiteral, values;
    integer channel;
    string formatLiteral;
    ref(Object) array values;
begin
    integer i, len, tokenStart, valueIndex, kind, dot;
    string token;

    len := formatLiteral.length();
    valueIndex := 1;
    i := 1;

scan:
    if i > len then
        goto done;

    if informatdelimiter(formatLiteral.charAt(i - 1)) then begin
        i := i + 1;
        goto scan
    end;

    tokenStart := i;
tokencollect:
    if i > len then
        goto tokenready;
    if informatdelimiter(formatLiteral.charAt(i - 1)) then
        goto tokenready;
    i := i + 1;
    goto tokencollect;
tokenready:
    token := formatLiteral.substring(tokenStart - 1, i - 1);
    kind := token.charAt(0);

    if kind = 73 | kind = 105 then begin
        if ~informatdigits(token.substring(1)) then
            informatsignalunsupported(token);
        values[valueIndex] := ininteger(channel)
    end else if kind = 65 | kind = 97 then begin
        if ~informatdigits(token.substring(1)) then
            informatsignalunsupported(token);
        values[valueIndex] := runtime_intoken(channel)
    end else if kind = 70 | kind = 102 then begin
        dot := token.indexOf(".");
        if dot < 0 then
            informatsignalunsupported(token);
        if ~informatdigits(token.substring(1, dot)) then
            informatsignalunsupported(token);
        if ~informatdigits(token.substring(dot + 1)) then
            informatsignalunsupported(token);
        values[valueIndex] := inreal(channel)
    end else
        informatsignalunsupported(token);

    valueIndex := valueIndex + 1;
    goto scan;
done:
end;

integer procedure ininteger(channel);
    value channel;
    integer channel;
begin
    ininteger := integer_parseInt(runtime_intoken(channel))
end;

real procedure inreal(channel);
    value channel;
    integer channel;
begin
    inreal := real_parseDouble(runtime_intoken(channel))
end;

integer procedure inchar(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    string token;

    token := runtime_intoken(channel);
    inchar := text.indexOf(token.charAt(0))
end
