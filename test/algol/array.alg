begin
	comment Source: Guennadi Levkine, "Hello Algol 60" program collection,
	        https://algol60.org/hello/2_array.a60.;
	integer array nArr[1:5];
	nArr[1] := 1;
	nArr[5] := 5;
	outinteger(1, nArr[5]);
	outinteger(1, nArr[3])
end
