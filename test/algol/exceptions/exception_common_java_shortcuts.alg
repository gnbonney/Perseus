comment Catch several common Java exceptions by direct name shortcuts.;
begin
   integer x;

   external java static(java.lang.Integer) integer procedure parseInt(string s);
   external java static(java.lang.Thread) procedure sleep(integer millis);

   x := 0;

   begin
      x := parseInt("not-a-number")
   exception
      when NumberFormatException do
         x := x + 1
   end;

   begin
      x := x + (1 div 0)
   exception
      when ArithmeticException do
         x := x + 10
   end;

   begin
      sleep(-1)
   exception
      when IllegalArgumentException do
         x := x + 100
   end;

   begin
      x := parseInt("still-not-a-number")
   exception
      when Exception do
         x := x + 1000
   end;

   outinteger(1, x)
end
