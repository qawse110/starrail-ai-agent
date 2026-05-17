#!/usr/bin/env python3
"""诊断 wiki_data.json 的问题"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})

# 1. Title vs 名称
print("=== 角色名问题: title key vs 名称字段 ===")
for title in list(chars.keys())[:20]:
    c = chars[title]
    name = c.get("名称", "")
    if name != title:
        print(f"  title={title!r} → name={name!r}  [差异!]")
    else:
        print(f"  title={title!r} → name={name!r}  [一致]")

# 2. 哪些角色没有技能数据
print("\n=== 无技能数据的角色（前20）===")
no_skill = []
for title, c in chars.items():
    has_skill = any(k.startswith("技能_") and "星魂" not in k for k in c.keys())
    if not has_skill:
        no_skill.append(title)
for t in no_skill[:20]:
    # 检查是否有角色/技能模板
    has_skill_template = any("技能_" in k for k in chars[t].keys())
    sample_keys = [k for k in chars[t].keys() if "技能" in k][:5]
    print(f"  {t}: skill_template={has_skill_template}, keys_with_技能={sample_keys}")

# 3. 光锥数据检查
cones = data.get("light_cones", {})
print(f"\n=== 光锥数据: {len(cones)}条 ===")
for title in list(cones.keys())[:5]:
    c = cones[title]
    name = c.get("名称", "")
    print(f"  {title!r} → name={name!r}, keys={list(c.keys())[:8]}")

# 4. 检查数据路径结构
print(f"\n=== 顶层keys ===")
for k in data.keys():
    v = data[k]
    if isinstance(v, dict):
        print(f"  {k}: dict({len(v)} items)")
    elif isinstance(v, int):
        print(f"  {k}: {v}")

# 5. 一些缺少必要字段的角色
print(f"\n=== 缺少名称字段的角色 ===")
for title, c in chars.items():
    if not c.get("名称", "").strip():
        print(f"  {title!r}: 名称字段为空或缺失")