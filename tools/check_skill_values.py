#!/usr/bin/env python3
"""显示角色所有技能_*字段的实际值"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})

# 绯英有76个技能_*字段，看看具体是哪些
print("=== 绯英所有技能_*字段（非空）===")
c = chars.get("绯英", {})
for k, v in sorted(c.items()):
    if k.startswith("技能_") and v.strip():
        print(f"  {k} = {str(v)[:120]}")

print("\n\n=== 绯英所有技能_*字段（空）===")
for k, v in sorted(c.items()):
    if k.startswith("技能_") and not v.strip():
        print(f"  {k} = (空)")

# 同样检查芮克先生
print("\n\n=== 芮克先生所有技能_*字段（非空）===")
c2 = chars.get("芮克先生", {})
for k, v in sorted(c2.items()):
    if k.startswith("技能_") and v.strip():
        print(f"  {k} = {str(v)[:120]}")

# 艾利欧
print("\n\n=== 艾利欧所有技能_*字段（非空）===")
c3 = chars.get("艾利欧", {})
for k, v in sorted(c3.items()):
    if k.startswith("技能_") and v.strip():
        print(f"  {k} = {str(v)[:120]}")