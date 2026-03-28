begin
	comment Source: Guennadi Levkine, "Hello Algol 60" program collection,
	        https://algol60.org/hello/3_boolean.a60.;
	comment no such library procedure outboolean(1,b);
	boolean b;
	b := true;
	if b then outstring(1, "true") else outstring(1, "false")
end
