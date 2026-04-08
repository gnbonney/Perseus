begin comment Test anonymous procedures capturing outer parameters, locals, and nested procedure names;
      integer procedure apply(f);
         integer procedure f;
      begin
         apply := f(5)
      end;

      integer procedure outer(base);
         value base;
         integer base;
      begin
         integer offset;

         integer procedure add(y);
            value y;
            integer y;
         begin
            add := y + base + offset
         end;

         offset := 7;
         outer := apply(proc (integer x) integer : (add(x) + base + offset))
      end;

      outinteger(1, outer(30))
end;
