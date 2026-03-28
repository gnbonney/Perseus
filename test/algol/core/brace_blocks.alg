{
    comment
        Perseus-specific hardware-representation regression.
        Intended to verify { and } anywhere begin and end are accepted;

    integer i, total;
    total := 0;

    for i := 1 step 1 until 3 do
    {
        if i < 3 then
        {
            total := total + i
        }
        else
        begin
            total := total + i
        end
    };

    if total = 6 then
    {
        outstring(1, "brace-blocks\n")
    }
    else
    begin
        outstring(1, "bad-brace-blocks\n")
    end
}
