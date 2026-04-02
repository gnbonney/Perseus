comment Use the compiled Perseus standard environment through builtin string names.;
begin
   string s, t;
   integer n;
   s := "Hello, world!";
   n := length(s);
   t := substring(s, 8, 12);
   s := concat(t, "!");
   outinteger(1, n);
   outstring(1, ",");
   outstring(1, t);
   outstring(1, ",");
   outstring(1, s)
end
