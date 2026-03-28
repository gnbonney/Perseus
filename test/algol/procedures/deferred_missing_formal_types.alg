begin
  comment Test: deferred parameter first in callback-style forwarding path. 
  use(p, x) forwards deferred parameter x to procedure p, called with int and real.;
  integer i;
  real  r;
  i := 5;
  r := 1.2;

  procedure inc(x); begin x := x + 1 end;
  procedure use(x);
  begin
    inc(x)
  end;
  use(i); % use wraps inc with deferred x
  use(r);

  outinteger(1, i); % 6
  outreal(1, r); % 2.2
end.