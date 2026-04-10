comment Java Iterable for-in sample for Milestone 43;
begin
    external java class java.util.ArrayList;
    external java class java.lang.Integer;
    external java static(java.lang.Integer)
        ref(Integer) procedure valueOf(integer boxedint);

    ref(ArrayList) values;
    integer item, sum;

    values := new ArrayList();
    values.add(valueOf(1));
    values.add(valueOf(2));
    values.add(valueOf(3));

    sum := 0;
    for item in values do
    begin
        sum := sum + item;
        item := 99
    end;

    outinteger(1, sum);
    outterminator(1);
    outinteger(1, values.size())
end
