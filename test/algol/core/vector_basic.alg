begin
    vector integer nums;

    nums.append(2);
    nums.append(4);
    nums.append(6);

    outinteger(1, length(nums));
    outterminator(1);
    outinteger(1, nums[1]);
    outterminator(1);

    nums[1] := 9;

    outinteger(1, nums.size);
    outterminator(1);
    outinteger(1, nums.size());
    outterminator(1);
    outinteger(1, nums[1])
end
