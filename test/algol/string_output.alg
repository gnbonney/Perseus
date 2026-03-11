begin
  string s;
  integer i;
  s := "Hello, world!";
  i := length(s);
  outstring(1, s);
  outterminator(1);
  outinteger(1, i);
  outterminator(1);
  outstring(1, s[1]);
  outterminator(1);
  outstring(1, substring(s, 8, 12));
  outterminator(1);
  s[8] := "W";
  outstring(1, s);
  outterminator(1);
  s := concat(s, "!!!");
  outstring(1, s)
end
