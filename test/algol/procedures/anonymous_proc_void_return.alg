begin comment Test returning an anonymous void procedure and passing it onward;
      procedure procedure make;
      begin
         make := proc () void : outstring(1, "Hello")
      end;

      procedure consume(f);
         procedure f;
      begin
         f()
      end;

      consume(make())
end;
