begin comment Test rebinding a typed procedure name to an anonymous procedure;
      integer procedure p;
      begin
         p := proc () integer : 42;
         outinteger(1, p)
      end;
      p
end;
