begin comment Test returning an anonymous integer procedure and passing it onward;
      integer procedure procedure make;
      begin
         make := proc () integer : 42
      end;

      procedure consume(f);
         integer procedure f;
      begin
         outinteger(1, f())
      end;

      consume(make())
end;
