comment Test: deferred typing with both value and name parameters.
Value typeless formals should adapt to integer and real actuals.
Name typeless formals should adapt to actual type and preserve assignment semantics.;
begin
  integer i;
  real r;
  i := 5;
  r := 5.5;

  procedure valTypeless(x);
  value x;
  begin
    outreal(1, x);
    outterminator(1)
  end;

  procedure nameTypeless(y);
  begin
    y := y + 1;
    outreal(1, y);
    outterminator(1)
  end;

  valTypeless(i);
  valTypeless(r);
  nameTypeless(i);
  nameTypeless(r);
end
