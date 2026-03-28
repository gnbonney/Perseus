begin
    boolean p, q, r;
    p := true;
    q := false;
    r := true;
    if p or q then outstring(1, "or works\n");
    if not q then outstring(1, "not works\n");
    if p and not q and r then outstring(1, "Boolean logic test passed\n")
end
