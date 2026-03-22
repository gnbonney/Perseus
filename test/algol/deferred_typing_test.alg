comment Test: deferred-typing direct assignment in a typeless name param procedure.  
p(x) uses x := x + 1 and is called with integer and real to verify split behavior.;
begin
  integer i;
  real r;
  i := 42;
  r := 3.14;
  procedure p(x); begin x := x + 1 end;
  p(i);
  p(r);
  outinteger(1, i);
  outreal(1, r)
end.