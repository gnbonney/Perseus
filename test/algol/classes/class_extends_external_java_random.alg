comment Draft Milestone 33 sample: Perseus class extending a concrete external Java class;
begin
    external java class java.util.Random;

    Random class Dice;
    begin
        integer procedure roll;
            roll := nextInt(6) + 1
    end;

    ref(Dice) d;

    d := new Dice;
    outinteger(1, d.roll)
end
