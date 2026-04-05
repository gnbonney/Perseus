external java class java.lang.Object;

begin
    ref(Object) array items[1:3];
    ref(Object) a;

    a := null;
    items[1] := new Object;
    items[2] := a;

    if items[1] <> null then outinteger(1, 1);
    if items[2] = null then outinteger(1, 2);

    a := items[1];
    if a = items[1] then outinteger(1, 3)
end
