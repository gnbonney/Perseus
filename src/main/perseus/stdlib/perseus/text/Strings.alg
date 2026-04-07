comment Standard environment string support.;
namespace perseus.text;

integer procedure length(str);
    value str;
    string str;
begin
    length := str.length()
end;

string procedure concat(a, b);
    value a, b;
    string a, b;
begin
    concat := a.concat(b)
end;

string procedure substring(str, i, j);
    value str, i, j;
    string str;
    integer i, j;
begin
    substring := str.substring(i - 1, j)
end
