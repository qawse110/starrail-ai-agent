#!/usr/bin/env python3
"""检查角色数据完整性"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

cs = data["characters"]

has_skills = 0
has_eidolons = 0
has_stats = 0
has_full = 0

for name, c in cs.items():
    skills = any(k.startswith("技能_") and k not in ["技能_名称","技能_描述","技能_类型"] for k in c.keys())
    eidolons = bool(c.get("技能_星魂1", ""))
    stats = "_stats" in c
    
    if skills: has_skills += 1
    if eidolons: has_eidolons += 1
    if stats: has_stats += 1
    if skills and eidolons and stats: has_full += 1

print(f"总角色: {len(cs)}")
print(f"有技能: {has_skills}")
print(f"有星魂: {has_eidolons}")
print(f"有属性: {has_stats}")
print(f"全部完整: {has_full}")

print("\n无技能数据的角色:")
for name, c in cs.items():
    has_skill_data = any(k.startswith("技能_") and not k.startswith("技能_星魂") and k not in ["技能_名称","技能_描述","技能_类型"] for k in c.keys())
    if not has_skill_data:
        print(f"  {name}")