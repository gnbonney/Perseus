begin
    external java class java.lang.Object;
    external java static(java.util.Objects)
        boolean procedure isNull(ref(Object) candidate);
    external java static(java.util.Objects)
        ref(Object) procedure requireNonNullElse(ref(Object) candidate; ref(Object) fallback);

    ref(Object) a, b, c;

    a := null;
    b := new Object;
    c := requireNonNullElse(a, b);

    if isNull(a) then outstring(1, "N");
    if c = b then outstring(1, "E");
    if isNull(c) then outstring(1, "X") else outstring(1, "O")
end
