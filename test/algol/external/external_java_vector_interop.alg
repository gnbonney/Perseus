comment External java vector interop sample for Milestone 43;
begin
    external java static(java.util.Collections)
        procedure reverse(vector integer values);
    external java static(java.util.Collections)
        vector integer procedure unmodifiableList(vector integer values);

    vector integer values, copy;
    integer item;

    values.append(1);
    values.append(2);
    values.append(3);

    reverse(values);
    copy := unmodifiableList(values);

    for item in copy do
    begin
        outinteger(1, item);
        outterminator(1)
    end
end
