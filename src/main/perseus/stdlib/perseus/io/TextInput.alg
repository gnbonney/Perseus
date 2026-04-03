comment Compiled standard-environment slice for text input helpers.;
namespace perseus.io;

external java static(gnb.perseus.runtime.TextInputSupport)
    integer procedure ininteger(integer channel) as runtime_ininteger;
external java static(gnb.perseus.runtime.TextInputSupport)
    real procedure inreal(integer channel) as runtime_inreal;
external java static(gnb.perseus.runtime.TextInputSupport)
    integer procedure inchar(integer channel; string text) as runtime_inchar;

integer procedure ininteger(channel);
    value channel;
    integer channel;
begin
    ininteger := runtime_ininteger(channel)
end;

real procedure inreal(channel);
    value channel;
    integer channel;
begin
    inreal := runtime_inreal(channel)
end;

integer procedure inchar(channel, text);
    value channel, text;
    integer channel;
    string text;
begin
    inchar := runtime_inchar(channel, text)
end
