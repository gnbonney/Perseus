begin
	comment Simplified pi program for testing;
	real a, b, anext, bnext;
	a := 4 * sqrt(3);
	b := 6;
	real procedure archimedesa(a, b);
		value a, b;
		real a, b;
		begin
			archimedesa := (2 * a * b) / (a + b);
		end archimedesa;
	real procedure archimedesb(anext, b);
		value anext, b;
		real anext, b;
		begin
			archimedesb := sqrt (anext * b);
		end archimedesb;
	integer i;
	for i := 1 step 1 until 10 do
		begin
			anext := archimedesa(a, b);
			bnext := archimedesb(anext, b);
			a := anext;
			b := bnext;
			outreal(1, (a / 2));
			outstring(1, " > pi > ");
			outreal(1, (b / 2));
			outstring(1, "\n");
		end loop;
end