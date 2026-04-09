begin comment Test anonymous procedures in higher-order numerical summation;
      real procedure sumof(n, term);
         value n;
         integer n;
         real procedure term;
      begin
         integer i;
         real total;
         total := 0.0;
         for i := 1 step 1 until n do
            total := total + term(i);
         sumof := total
      end;

      outreal(1, sumof(5, proc (integer i) real : (1.0 / i)));
      outstring(1, "\n");
      outreal(1, sumof(4, proc (integer i) real :
         begin
            real x;
            x := i;
            x * x
         end))
end;
