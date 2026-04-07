comment Console input channel validation sample for Milestone 40.;
begin
   integer x, rejected;

   x := 0;
   rejected := 0;

   begin
      ininteger(0, x)
   exception
      when IllegalArgumentException do
         rejected := 1
   end;

   outinteger(1, rejected)
end
