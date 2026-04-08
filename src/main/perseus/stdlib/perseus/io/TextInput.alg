comment Standard environment text input support.;
namespace perseus.io;

external java class java.lang.Object;
external java class java.lang.IllegalArgumentException;
external java static(java.lang.Integer)
    integer procedure parseInt(string text) as integer_parseInt;
external java static(java.lang.Double)
    real procedure parseDouble(string text) as real_parseDouble;
external(perseus.io.Channels)
    string procedure intoken(integer channel) as channel_intoken;

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

procedure informatsignalmismatch;
begin
    signal new IllegalArgumentException("format/argument count mismatch")
end;

procedure informatvalues(channel, formatLiteral, values, expectedKinds);
    value channel, formatLiteral, values, expectedKinds;
    integer channel;
    string formatLiteral;
    ref(Object) array values;
    string expectedKinds;
begin
    integer i, len, tokenStart, valueIndex, kind, dot, expectedKind;
    string token;
    boolean scanning, collecting, skipping;

    len := formatLiteral.length();
    valueIndex := 1;
    i := 1;
    scanning := true;

    while scanning do begin
        skipping := true;
        while skipping do
            if i > len then
                skipping := false
            else if informatdelimiter(formatLiteral.charAt(i - 1)) then
                i := i + 1
            else
                skipping := false;

        if i > len then
            scanning := false
        else begin
            tokenStart := i;
            collecting := true;
            while collecting do
                if i > len then
                    collecting := false
                else if informatdelimiter(formatLiteral.charAt(i - 1)) then
                    collecting := false
                else
                    i := i + 1;

            token := formatLiteral.substring(tokenStart - 1, i - 1);
            if valueIndex > expectedKinds.length() then
                informatsignalmismatch;
            kind := token.charAt(0);
            expectedKind := expectedKinds.charAt(valueIndex - 1);

            if kind = 73 | kind = 105 then begin
                if expectedKind <> 73 then
                    informatsignalmismatch;
                if ~informatdigits(token.substring(1)) then
                    informatsignalunsupported(token);
                values[valueIndex] := ininteger(channel)
            end else if kind = 65 | kind = 97 then begin
                if expectedKind <> 65 then
                    informatsignalmismatch;
                if ~informatdigits(token.substring(1)) then
                    informatsignalunsupported(token);
                values[valueIndex] := channel_intoken(channel)
            end else if kind = 70 | kind = 102 then begin
                if expectedKind <> 70 then
                    informatsignalmismatch;
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

            valueIndex := valueIndex + 1
        end
    end;

    if valueIndex <> expectedKinds.length() + 1 then
        informatsignalmismatch
end;

integer procedure ininteger(channel);
    value channel;
    integer channel;
begin
    ininteger := integer_parseInt(channel_intoken(channel))
end;

real procedure inreal(channel);
    value channel;
    integer channel;
begin
    inreal := real_parseDouble(channel_intoken(channel))
end;

integer procedure inchar(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    string token;

    token := channel_intoken(channel);
    inchar := text.indexOf(token.charAt(0))
end
