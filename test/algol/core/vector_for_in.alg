begin
    vector integer nums;
    integer item, count, total;

    nums.append(2);
    nums.append(4);
    nums.append(6);

    count := 0;
    total := 0;

    for item in nums do
    begin
        total := total + item;
        item := 100;
        count := count + 1
    end;

    outinteger(1, total);
    outterminator(1);
    outinteger(1, count);
    outterminator(1);
    outinteger(1, nums[0]);
    outterminator(1);
    outinteger(1, nums[1]);
    outterminator(1);
    outinteger(1, nums[2])
end
