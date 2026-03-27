comment Catch a Java exception thrown through external java interop.;
begin
   integer x;

   external java static(java.lang.Integer) integer procedure parseInt(string s);

   x := -1;

   begin
      x := parseInt("not-a-number")
   exception
      when java(java.lang.NumberFormatException) do
         x := 0
   end;

   outinteger(1, x)
end
