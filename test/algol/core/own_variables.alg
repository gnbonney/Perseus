begin
   procedure accumulate;
   begin
      own integer counter;
      own integer array history[1:1];
      counter := counter + 1;
      history[1] := history[1] + counter;
      outinteger(1, history[1])
   end;

   accumulate;
   outterminator(1);
   accumulate;
   outterminator(1);
   accumulate
end
