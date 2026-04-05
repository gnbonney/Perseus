comment Compiled standard-environment slice for text output helpers.;
namespace perseus.io;

external java class java.io.PrintStream;
external java class java.lang.Object;
external java static(gnb.perseus.runtime.Channels)
    procedure outString(integer channel; string text) as runtime_outstring;
external java static(gnb.perseus.runtime.TextFormatSupport)
    string procedure format(string formatLiteral; ref(Object) array values) as runtime_format;
external java static(java.lang.Double)
    string procedure toString(real number) as real_toString;
external java static(java.lang.Integer)
    string procedure toString(integer number) as integer_toString;

external java static(java.lang.System)
    ref(PrintStream) out as stdout;
external java static(java.lang.System)
    ref(PrintStream) err as stderr;

string procedure formatvalues(formatLiteral, values);
    value formatLiteral, values;
    string formatLiteral;
    ref(Object) array values;
begin
    formatvalues := runtime_format(formatLiteral, values)
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
