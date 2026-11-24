import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
for node in re.findall(r"<node [^>]*>", s):
    t = re.search(r' text="([^"]*)"', node)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
    if t and b and t.group(1).startswith("[VIDEO]"):
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
