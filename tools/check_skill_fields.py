#!/usr/bin/env python3
"""检查角色技能字段具体值"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})

# 检查几个角色的技能字段实际值
targets = ["绯英", "希儿", "艾丝妲", "白露", "丹恒", "黑塔", "三月七•存护", "芮克先生"]
print("=== 角色技能字段具体值 ===\n")
for t in targets:
    c = chars.get(t)
    if not c:
        print(f"[{t}] 不存在\n")
        continue
    print(f"[{t}]")
    # 检查普攻
    for skill_key in ["技能_普攻", "技能_普攻描述", "技能_普攻TAG", "技能_普攻削韧值"]:
        val = c.get(skill_key, "")
        print(f"  {skill_key} = {str(val)[:80]!r}")
    for skill_key in ["技能_战技", "技能_战技描述"]:
        val = c.get(skill_key, "")
        print(f"  {skill_key} = {str(val)[:80]!r}")
    for skill_key in ["技能_终结技", "技能_终结技描述"]:
        val = c.get(skill_key, "")
        print(f"  {skill_key} = {str(val)[:80]!r}")
    for skill_key in ["技能_天赋", "技能_天赋描述"]:
        val = c.get(skill_key, "")
        print(f"  {skill_key} = {str(val)[:80]!r}")
    for skill_key in ["技能_秘技", "技能_秘技描述"]:
        val = c.get(skill_key, "")
        print(f"  {skill_key} = {str(val)[:80]!r}")
    # 星魂
    for i in range(1, 4):
        val = c.get(f"技能_星魂{i}", "")
        print(f"  技能_星魂{i} = {str(val)[:60]!r}")
    # 检查是否有{{角色/技能}}模板的数据
    has_skill_prefix = any(k.startswith("技能_") for k in c.keys())
    skill_keys = [k for k in c.keys() if k.startswith("技能_")]
    print(f"  技能_* keys总数: {len(skill_keys)}")
    print()

# 统计全面
print("=== 统计 ===")
total = len(chars)
with_skills = {"普攻": 0, "战技": 0, "终结技": 0, "天赋": 0, "秘技": 0}
for c in chars.values():
    for k in with_skills:
        wiki_key = f"技能_{k}"
        if c.get(wiki_key, "").strip():
            with_skills[k] += 1
print(f"总角色: {total}")
for k, v in with_skills.items():
    print(f"  有技能_{k} (名称): {v}/{total}")

# 检查普攻名称为空但有描述的情况
print("\n=== 普攻名称为空但有描述 ===")
for t, c in chars.items():
    name = c.get("技能_普攻", "").strip()
    desc = c.get("技能_普攻描述", "").strip()
    if not name and desc:
        print(f"  {t}: 普攻描述={desc[:60]!r}")