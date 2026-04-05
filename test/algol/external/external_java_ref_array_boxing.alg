external java class java.lang.Object;

begin
    ref(Object) array items[1:3];
    ref(Object) temp;

    items[1] := 1;
    items[2] := 2.5;
    items[3] := "hi";

    temp := items[1];
    outstring(1, temp.toString);
    outterminator(1);
    temp := items[2];
    outstring(1, temp.toString);
    outterminator(1);
    temp := items[3];
    outstring(1, temp.toString)
end
