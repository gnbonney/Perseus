begin
    external java class java.nio.file.StandardOpenOption;

    external java static(java.lang.Double)
        real MAX_VALUE as doublemax;
    external java static(java.lang.Integer)
        integer MAX_VALUE as intmax;
    external java static(java.nio.file.StandardOpenOption)
        ref(StandardOpenOption) READ as readmode;

    if doublemax > 1.0 then outstring(1, "R") else outstring(1, "r");
    if intmax > 1 then outstring(1, "I") else outstring(1, "i");
    outstring(1, readmode.toString())
end
