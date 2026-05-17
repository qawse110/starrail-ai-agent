#!/usr/bin/env python3
"""统计每个角色的技能数量"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

cs = data["characters"]

SKILL_KEYS = {"技能_普攻":"普攻","技能_战技":"战技","技能_终结技":"终结技","技能_天赋":"天赋","技能_秘技":"秘技"}

print("=== 角色技能统计 ===")
for name in sorted(cs.keys()):
    c = cs[name]
    skills = [label for k,label in SKILL_KEYS.items() if k in c and c[k]]
    print(f"{name:10s}: {len(skills)}个技能 ({', '.join(skills)})")

counts = {name: sum(1 for k in SKILL_KEYS if k in cs[name] and cs[name][k]) for name in cs}
print(f"完整(5): {sum(1 for v in counts.values() if v == 5)}")
print(f"4个技能: {sum(1 for v in counts.values() if v == 4)}")
print(f"3个技能: {sum(1 for v in counts.values() if v == 3)}")
print(f"2个技能: {sum(1 for v in counts.values() if v == 2)}")
print(f"1个技能: {sum(1 for v in counts.values() if v == 1)}")
print(f"0个技能: {sum(1 for v in counts.values() if v == 0)}")