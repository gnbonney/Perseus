begin
    comment Algol program print the primes less than 1000 using the 
            sieve method.;
    Boolean array sieve[2:1000];
    integer p, count;

    comment Eliminate the multiples of the argument prime number;
    procedure eliminate(p);
	integer p;
	begin
	    integer i;
	    for i := 2*p step p until 1000 do
	        sieve[i] := false
	end;

    comment Clear the sieve.;
    integer i;
    for i := 2 step 1 until 1000 do
	sieve[i] := true;

    comment Find the primes in range.;
    for i := 2 step 1 until 1000 do
	if sieve[i] then
	    eliminate(i);

    comment Print the results, 10 per line.;
    p := 2;
    for count := 0 while p <= 1000 do begin
        comment This construct is similar to a do .. while built with a goto.;
        makeline:
            if sieve[p] then begin
	        outinteger(1,p);
		outstring(1," ");
		count := count + 1
	    end;
	    p := p + 1;
	    if p <= 1000 & count < 10 then goto makeline;
	outstring(1,"\n")
    end

end
