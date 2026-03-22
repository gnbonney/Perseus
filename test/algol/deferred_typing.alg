comment Test: deferred typing with both value and name parameters.  
value typeless formal should adapt to actual type (integer/real) and print real.  
name typeless formal should adapt to actual type and preserve assignment semantics.;
begin
  integer i;
  real r;
  i := 5;
  r := 5.5;

  procedure valTypeless(x);  /* typeless formal, value mode */
  value x;
  begin
    outreal(1, x);
  end;

  procedure nameTypeless(y); /* typeless formal, name mode */
  begin
    y := y + 1;
    outreal(1, y);
  end;

  valTypeless(i);
  valTypeless(r);
  nameTypeless(i);
  nameTypeless(r);
end
