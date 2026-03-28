begin
   integer stage, mode, total;
   comment
      A small score-computation state machine driven by switch declarations.
      The third switch entry is itself another switch (branch[mode]).
      The fourth entry uses an inline if, and because switch entries are
      evaluated at jump time, changing total later changes where stage 4 jumps.;
   switch branch := doublephase, triplephase;
   switch steps := seed, squarephase, branch[mode], if total > 20 then report else bonus;

   stage := 1;
   mode := 1;
   total := 0;

   dispatch:
   goto steps[stage];

   seed:
   total := 1;
   stage := 2;
   goto dispatch;

   squarephase:
   total := total + stage * stage;
   stage := 3;
   goto dispatch;

   doublephase:
   total := total + 2 * stage;
   mode := 2;
   goto dispatch;

   triplephase:
   total := total + 3 * stage;
   stage := 4;
   goto dispatch;

   bonus:
   total := total + 5;
   goto dispatch;

   report:
   outinteger(1, total)
end
