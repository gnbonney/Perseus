comment Compiled standard-environment slice for text output helpers.;
namespace perseus.io;

external java class java.io.PrintStream;

external java static(java.lang.System)
    ref(PrintStream) out as stdout;
external java static(java.lang.System)
    ref(PrintStream) err as stderr;

procedure outstring(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    if channel = 0 then stderr.print(text)
    else stdout.print(text)
end;

procedure outreal(channel, number);
    value channel, number;
    integer channel;
    real number;
begin
    if channel = 0 then stderr.print(number)
    else stdout.print(number)
end;

procedure outinteger(channel, number);
    value channel, number;
    integer channel, number;
begin
    if channel = 0 then stderr.print(number)
    else stdout.print(number)
end;

procedure outterminator(channel);
    value channel;
    integer channel;
begin
    if channel = 0 then stderr.print(" ")
    else stdout.print(" ")
end;

procedure outchar(channel, text, position);
    value channel, text, position;
    integer channel, position;
    string text;
begin
    outstring(channel, text.substring(position, position + 1))
end
