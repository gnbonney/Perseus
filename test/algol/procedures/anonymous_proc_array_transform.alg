begin comment Test anonymous procedures in array-oriented numerical transformation;
      procedure transform(a, lo, hi, f);
         value lo, hi;
         integer lo, hi;
         real array a;
         real procedure f;
      begin
         integer i;
         for i := lo step 1 until hi do
            a[i] := f(a[i])
      end;

      real array data[1:4];
      integer i;

      for i := 1 step 1 until 4 do
         data[i] := i;

      transform(data, 1, 4, proc (real x) real :
         begin
            real shifted;
            shifted := x + 1.0;
            shifted * shifted
         end);

      for i := 1 step 1 until 4 do
      begin
         outreal(1, data[i]);
         outstring(1, "\n")
      end
end;
