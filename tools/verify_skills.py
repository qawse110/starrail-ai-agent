#!/usr/bin/env python3
"""验证老角色技能数据是否成功提取"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})

# 检查几个典型老角色
targets = ["希儿", "白露", "丹恒", "艾丝妲", "布洛妮娅", "姬子", "景元", "刃", "卡芙卡"]
print("=== 老角色技能数据验证 ===\n")
for t in targets:
    c = chars.get(t)
    if not c:
        print(f"[{t}] 不存在\n")
        continue
    skills = {}
    for skill_type in ["普攻", "战技", "终结技", "天赋", "秘技"]:
        # 检查 技能_普攻 格式
        name = c.get(f"技能_{skill_type}", "") or c.get(skill_type, "")
        desc = c.get(f"技能_{skill_type}描述", "") or c.get(f"{skill_type}描述", "")
        if name:
            skills[skill_type] = (name, desc[:60] if desc else "")
    if skills:
        print(f"[{t}] ✅ {len(skills)}个技能:")
        for k, (n, d) in skills.items():
            print(f"  {k}: {n} | {d}...")
    else:
        # 检查是否有技能_名称
        sn = c.get("技能_名称", "")
        if sn:
            print(f"[{t}] 🔶 新格式: {sn} ({c.get('技能_类型','')})")
        else:
            print(f"[{t}] ❌ 完全无技能数据")

# 统计有多少角色有旧格式技能
print("\n=== 统计 ===")
old_format = 0
new_format = 0
both = 0
none = 0
for t, c in chars.items():
    has_old = any(c.get(f"技能_{k}", "") for k in ["普攻","战技","终结技","天赋","秘技"])
    has_new = bool(c.get("技能_名称", ""))
    if has_old and has_new: both += 1
    elif has_old: old_format += 1
    elif has_new: new_format += 1
    else: none += 1
print(f"旧格式(技能_普攻等): {old_format}")
print(f"新格式(技能_名称): {new_format}")
print(f"两种都有: {both}")
print(f"无技能数据: {none}")
print(f"总计: {len(chars)}")