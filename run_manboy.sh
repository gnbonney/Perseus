#!/bin/bash
cd /c/Users/gnbon/Projects/JAlgol
gradle build -x test >/dev/null 2>&1
echo "" | java -cp build/test-algol gnb.jalgol.programs.ManBoy
