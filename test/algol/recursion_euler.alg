real procedure euler(fct, eps, tim);
    value eps, tim;
    real procedure fct; real eps; integer tim;
    begin
    integer i, k, n, r;
    array m[0:15];
    real mn, mp, ds, sum;
    n := t := 0;
    m[0] := fct(0); sum := m[0]/2;
    for i := 1, i + 1 while t < tim do
        begin
        mn := fct(i);
        for k := 0 step 1 until n do
            begin
            mp := (mn + m[k])/2;
            m[k] := mn; mn := mp
            end;
        if abs(mn) < abs(m[n]) and n < 15 then
            begin
            ds := mn/2; n := n + 1;
            m[n] := mn
            end
        else
            ds := mn;
        sum := sum + ds;
        t := if abs(ds) < eps then t + 1 else 0
        end;
    euler := sum
    end euler
