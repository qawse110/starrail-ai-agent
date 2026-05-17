#!/usr/bin/env python3
"""分析现有wiki_data.json中角色的技能/数据字段"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})

# 找几个有技能数据的角色看看
print("=== 有技能_* 字段的角色 ===")
for title, c in chars.items():
    skill_keys = [k for k in c.keys() if k.startswith("技能_") and "星魂" not in k]
    if skill_keys:
        print(f"\n{title}: {len(skill_keys)} 技能字段")
        for k in skill_keys[:15]:
            v = c[k]
            print(f"  {k} = {str(v)[:100]}")

print("\n\n=== 有 '名称' 字段 != title 的角色 ===")
for title, c in chars.items():
    name = c.get("名称", "")
    if name and name != title:
        print(f"  title={title!r} → 名称={name!r}")

print("\n\n=== 光锥数据检查: 名称为空的光锥 ===")
cones = data.get("light_cones", {})
empty_name = []
for title, c in cones.items():
    name = c.get("名称", "").strip()
    if not name:
        empty_name.append(title)
print(f"  空名称光锥: {len(empty_name)}/{len(cones)}")
for t in empty_name[:10]:
    print(f"    {t!r}: 名称={c.get('名称','')!r}")

# 检查光锥实际keys
print(f"\n=== 光锥示例: 前3条完整keys ===")
for title in list(cones.keys())[:3]:
    c = cones[title]
    name = c.get("名称", "").strip() or title
    print(f"  {name}: {list(c.keys())}")

# 统计光锥有哪些字段
all_cone_keys = set()
for c in cones.values():
    all_cone_keys.update(c.keys())
print(f"\n光锥所有可能的字段: {sorted(all_cone_keys)}")