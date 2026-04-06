comment Compiled standard-environment slice for text input helpers.;
namespace perseus.io;

external java class java.lang.Object;
external java static(gnb.perseus.runtime.Channels)
    integer procedure ininteger(integer channel) as runtime_ininteger;
external java static(gnb.perseus.runtime.Channels)
    real procedure inreal(integer channel) as runtime_inreal;
external java static(gnb.perseus.runtime.Channels)
    integer procedure inchar(integer channel; string text) as runtime_inchar;
external java static(gnb.perseus.runtime.Channels)
    procedure informatValuesInto(integer channel; string formatLiteral; ref(Object) array values) as runtime_informatValuesInto;

procedure informatvalues(channel, formatLiteral, values);
    value channel, formatLiteral, values;
    integer channel;
    string formatLiteral;
    ref(Object) array values;
begin
    runtime_informatValuesInto(channel, formatLiteral, values)
end;

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
