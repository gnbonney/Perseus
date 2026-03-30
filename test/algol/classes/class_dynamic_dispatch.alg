comment Draft Milestone 33 sample: prefix inheritance with dynamic dispatch;
begin
    class Animal;
    begin
        string procedure speak;
            speak := "animal"
    end;

    Animal class Dog;
    begin
        string procedure speak;
            speak := "dog"
    end;

    ref(Animal) pet;

    pet := new Dog;
    outstring(1, pet.speak)
end
