begin
	comment Source: Juliet Kemp, "ALGOL: The Language of Academia",
	        Linux Voice, https://www.linuxvoice.com/algol-the-language-of-academia/.;
	integer N, M; 
	N := 12; 
	begin 
		integer procedure oneton(N); 
			value N; 
			integer N; 
			begin 
				integer i; 
				for i := 1 step 1 until N do 
					begin 
						outinteger (1, i); 
						outstring (1, "\n"); 
					end loop; 
					oneton := (N * 2); 
			end oneton; 
		M := oneton(N); 
		outinteger(1, M); 
	end all;
end