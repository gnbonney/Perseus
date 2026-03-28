begin 
	comment Source: Juliet Kemp, "ALGOL: The Language of Academia",
	        Linux Voice, https://www.linuxvoice.com/algol-the-language-of-academia/.;
	comment This program uses the method Archimedes developed of finding pi, 
	by drawing a polygon just around a circle, a polygon just inside the unit circle, 
	and using these as an upper and lower bound on the circumference of the circle.;
	real a, b, anext, bnext; 
	a := 4 * sqrt(3); 
	b := 6; 
	begin 
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
	end all;
end