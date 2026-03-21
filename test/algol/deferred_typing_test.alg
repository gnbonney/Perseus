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