begin
  comment Test: mixed-type deferred name parameter (integer and real) in the same proc called twice.;
  integer i;
  real r;
  i := 10;
  r := 2.5;

  procedure p(x); 
  begin
    x := x + 1
  end;

  p(i); % i is integer
  p(r); % r is real
  outinteger(1, i); % expect 11
  outreal(1, r); % expect 3.5
end.