comment Compiled standard-environment slice for fault handling.;
namespace perseus.runtime;

external java static(gnb.perseus.runtime.FaultSupport)
    procedure fault(string text; real code) as runtime_fault;

procedure fault(text, code);
    value text, code;
    string text;
    real code;
begin
    runtime_fault(text, code)
end
