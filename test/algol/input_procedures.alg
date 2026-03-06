begin
  integer x, pos;
  real y;
  
  x := 0;
  y := 0.0;
  pos := 0;
  
  outstring(1, "Enter an integer: ");
  ininteger(1, x);
  
  outstring(1, "Enter a real: ");
  inreal(1, y);
  
  outstring(1, "Enter a character to find in 'Hello': ");
  inchar(1, "Hello", pos);
  
  outstring(1, "You entered integer: ");
  outinteger(1, x);
  outterminator(1);
  
  outstring(1, "You entered real: ");
  outreal(1, y);
  outterminator(1);
  
  outstring(1, "Character position in 'Hello': ");
  outinteger(1, pos);
  outterminator(1)
end
