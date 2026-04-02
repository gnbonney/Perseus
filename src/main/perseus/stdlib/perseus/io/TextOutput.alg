comment Compiled standard-environment slice for text output helpers.;
namespace perseus.io;

external java static(gnb.perseus.runtime.TextOutputSupport)
    procedure outstring(integer channel; string text) as runtime_outstring;
external java static(gnb.perseus.runtime.TextOutputSupport)
    procedure outreal(integer channel; real number) as runtime_outreal;
external java static(gnb.perseus.runtime.TextOutputSupport)
    procedure outinteger(integer channel; integer number) as runtime_outinteger;
external java static(gnb.perseus.runtime.TextOutputSupport)
    procedure outterminator(integer channel) as runtime_outterminator;
external java static(gnb.perseus.runtime.TextOutputSupport)
    procedure outchar(integer channel; string text; integer position) as runtime_outchar;

procedure outstring(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    runtime_outstring(channel, text)
end;

procedure outreal(channel, number);
    value channel, number;
    integer channel;
    real number;
begin
    runtime_outreal(channel, number)
end;

procedure outinteger(channel, number);
    value channel, number;
    integer channel, number;
begin
    runtime_outinteger(channel, number)
end;

procedure outterminator(channel);
    value channel;
    integer channel;
begin
    runtime_outterminator(channel)
end;

procedure outchar(channel, text, position);
    value channel, text, position;
    integer channel, position;
    string text;
begin
    runtime_outchar(channel, text, position)
end
