begin
    external java class java.lang.Object;
    external java static(java.lang.String)
        string procedure format(string pattern; ref(Object) array values);

    ref(Object) array values[1:3];

    values[1] := 42;
    values[2] := 2.5;
    values[3] := "hi";

    outstring(1, format("%s|%s|%s", values))
end
