comment Catch a language-level bounds error from array indexing.;
begin
   integer array a[1:3];
   integer x;

   x := -1;

   begin
      x := a[10]
   exception
      when BoundsError do
         x := 99
   end;

   outinteger(1, x)
end
