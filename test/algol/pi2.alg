begin
	comment Call-by-name version instead of call-by-value;
	real a, b; 
	a := 4 * sqrt(3); 
	b := 6; 
	begin 
		procedure archimedes(a, b); 
			real a, b; 
			begin 
				a := (2 * a * b) / (a + b); 
				b := sqrt (a * b); 
			end archimedes; 
		procedure archprint; 
			begin 
				outreal(1, (a / 2)); 
				outstring(1, “ > pi > “); 
				outreal(1, (b / 2)); 
				outstring(1, “n”); 
			end archprint; 
		integer i; 
		for i := 1 step 1 until 10 do 
			begin 
				archimedes(a, b); 
				archprint; 
			end loop; 
	end all;
end