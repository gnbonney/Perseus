external java class java.lang.Object;

begin
    ref(Object) a, b;

    a := null;
    b := new Object;

    if a = null then outinteger(1, 1);
    if b <> null then outinteger(1, 2);

    a := b;
    if a = b then outinteger(1, 3)
end
