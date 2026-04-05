comment Compiled standard-environment slice for text output helpers.;
namespace perseus.io;

external java class java.io.PrintStream;
external java static(gnb.perseus.runtime.Channels)
    procedure outString(integer channel; string text) as runtime_outstring;
external java static(gnb.perseus.runtime.Channels)
    procedure outReal(integer channel; real number) as runtime_outreal;
external java static(gnb.perseus.runtime.Channels)
    procedure outInteger(integer channel; integer number) as runtime_outinteger;
external java static(gnb.perseus.runtime.Channels)
    procedure outTerminator(integer channel) as runtime_outterminator;

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
    else runtime_outreal(channel, number)
end;

procedure outinteger(channel, number);
    value channel, number;
    integer channel, number;
begin
    if channel = 0 then stderr.print(number)
    else if channel = 1 then stdout.print(number)
    else runtime_outinteger(channel, number)
end;

procedure outterminator(channel);
    value channel;
    integer channel;
begin
    if channel = 0 then stderr.print(" ")
    else if channel = 1 then stdout.print(" ")
    else runtime_outterminator(channel)
end;

procedure outchar(channel, text, position);
    value channel, text, position;
    integer channel, position;
    string text;
begin
    outstring(channel, text.substring(position, position + 1))
end
