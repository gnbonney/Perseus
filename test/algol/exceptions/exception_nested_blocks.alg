comment Nested exception blocks should handle failures at the nearest enclosing block.;
begin
   integer x;

   external java static(java.lang.Integer) integer procedure parseInt(string s);

   x := -1;

   begin
      begin
         x := parseInt("not-a-number")
      exception
         when java(java.lang.NumberFormatException) do
            x := 1
      end;

      x := x + 10
   exception
      when java(java.lang.NumberFormatException) do
         x := 99
   end;

   outinteger(1, x)
end
