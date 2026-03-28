begin
    comment Based on the nested digits helper in Appendix 2 of the
            Algol 60 Modified Report (`outinteger`), adapted into a
            focused regression case for nested non-local access.
            Expected output if Milestone 21 is complete:
            3 1 4 8 3
            2 7 9 2;

    procedure traceDigits(number);
        value number;
        integer number;
    begin
        integer sum, count;

        procedure digits(n);
            value n;
            integer n;
        begin
            integer j, digit;
            j := entier(n / 10);
            digit := n - 10 * j;
            if j <> 0 then
                digits(j);
            outinteger(1, digit);
            outterminator(1);
            sum := sum + digit;
            count := count + 1
        end digits;

        sum := 0;
        count := 0;
        digits(number);
        outinteger(1, sum);
        outterminator(1);
        outinteger(1, count);
        outstring(1, "\n")
    end;

    traceDigits(314);
    traceDigits(27)
end
