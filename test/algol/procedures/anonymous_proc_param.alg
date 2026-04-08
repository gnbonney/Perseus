begin comment Test anonymous procedures passed to procedure parameters;
      procedure apply(f);
         integer procedure f;
      begin
         outinteger(1, f(21))
      end;
      apply(proc (integer x) integer : (x * 2))
end;
