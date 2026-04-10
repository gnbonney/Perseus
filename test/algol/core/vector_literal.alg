begin
    vector integer nums;
    integer item, total;

    nums := [2, 4, 6];
    total := 0;

    for item in nums do
        total := total + item;

    outinteger(1, length(nums));
    outterminator(1);
    outinteger(1, nums[0]);
    outterminator(1);
    outinteger(1, nums[2]);
    outterminator(1);
    outinteger(1, total)
end
