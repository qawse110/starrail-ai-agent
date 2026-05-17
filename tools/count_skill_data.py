#!/usr/bin/env python3
"""统计哪些角色有技能数据"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})

has_skill_name = []
has_skill_type = []
has_skill_desc = []
has_eidolon_1 = []
total = len(chars)

for title, c in chars.items():
    if c.get("技能_名称", "").strip():
        has_skill_name.append(title)
    if c.get("技能_类型", "").strip():
        has_skill_type.append(title)
    if c.get("技能_描述", "").strip():
        has_skill_desc.append(title)
    if c.get("技能_星魂1", "").strip() or c.get("星魂1", "").strip():
        has_eidolon_1.append(title)

print(f"总角色: {total}")
print(f"有技能_名称: {len(has_skill_name)}")
print(f"有技能_类型: {len(has_skill_type)}")
print(f"有技能_描述: {len(has_skill_desc)}")
print(f"有星魂1: {len(has_eidolon_1)}")
print()
print("有技能_名称的角色:")
for t in has_skill_name[:20]:
    cname = c.get("名称","")
    print(f"  {t} (类型={c.get('技能_类型','')})")
if len(has_skill_name) > 20:
    print(f"  ...共{len(has_skill_name)}个")

print()
print("无技能_名称但有星魂1的角色:")
for t in sorted(set(chars.keys()) - set(has_skill_name)):
    if t in has_eidolon_1:
        print(f"  {t}")
print(f"  共{len(set(chars.keys()) - set(has_skill_name))}个无技能名称")
print()
print("无星魂1也无技能名称的角色 (只有基础数据):")
for t in sorted(chars.keys()):
    if t not in has_skill_name and t not in has_eidolon_1:
        print(f"  {t}")