comment Standard environment fault support.;
namespace perseus.runtime;

external java class java.lang.RuntimeException;

procedure fault(text, code);
    value text, code;
    string text;
    real code;
begin
    signal new RuntimeException(text)
end
