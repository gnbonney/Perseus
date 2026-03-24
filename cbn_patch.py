import os

path = r'c:\Users\gnbon\Projects\Perseus\src\main\java\gnb\perseus\compiler\CodeGenerator.java'
with open(path, 'rb') as f:
    content = f.read()

# Look for the class declaration and first field
old_text = b'public class CodeGenerator extends AlgolBaseListener {\r\n    private final String source;'
# The od output showed weird indentation/spacing... let's try a regex approach in python if direct fails
# But first let's try what we think it is based on od -c

# Wait, od -c showed:
# 0000300   /  \r  \n   p   u   b   l   i   c       c   l   a   s   s
# 0000320   C   o   d   e   G   e   n   e   r   a   t   o   r       e   x
# 0000340   t   e   n   d   s       A   l   g   o   l   B   a   s   e   L
# 0000360   i   s   t   e   n   e   r       {  \r  \n                   p
# 0000400   r   i   v   a   t   e       f   i   n   a   l       S   t   r

# That's a LOT of spaces (represented as \x20 or just ' ') after { \r \n
# It looks like about 19 spaces?

new_method = b'''    private Map<String, Integer> procVarSlots = new HashMap<>();

    public void setProcVarSlots(Map<String, Integer> procVarSlots) {
        this.procVarSlots = procVarSlots;
    }

'''

target = b'public class CodeGenerator extends AlgolBaseListener {'
if target in content:
    parts = content.split(target, 1)
    # Find the first { and the following newline
    rest = parts[1]
    brace_idx = rest.find(b'{')
    nl_idx = rest.find(b'\n', brace_idx)
    
    new_content = parts[0] + target + rest[:nl_idx+1] + new_method + rest[nl_idx+1:]
    with open(path, 'wb') as f:
        f.write(new_content)
    print("Patch applied")
else:
    print("Target not found")
