comment Catch a common Java exception by name without external java class declarations.;
begin
   integer x;

   external java static(java.lang.Integer) integer procedure parseInt(string s);

   x := -1;

   begin
      x := parseInt("not-a-number")
   exception
      when NumberFormatException do
         x := 0
   end;

   outinteger(1, x)
end
