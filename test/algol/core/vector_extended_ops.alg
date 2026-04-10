begin
    vector integer nums;
    integer removed;
    boolean hasfour;

    nums := [2, 6];
    nums.insert(1, 4);
    hasfour := nums.contains(4);
    removed := nums.remove(0);

    outinteger(1, removed);
    outterminator(1);
    outinteger(1, nums[0]);
    outterminator(1);
    outinteger(1, length(nums));
    outterminator(1);
    if hasfour then
        outinteger(1, 1)
    else
        outinteger(1, 0);
    outterminator(1);

    nums.clear();
    outinteger(1, length(nums))
end
