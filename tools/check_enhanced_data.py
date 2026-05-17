#!/usr/bin/env python3
"""检查增强后的 wiki_data.json"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

chars = data.get("characters", {})
total = len(chars)

with_stats = sum(1 for c in chars.values() if isinstance(c, dict) and "_stats" in c)
with_skills = sum(1 for c in chars.values() if isinstance(c, dict) and any(k.startswith("技能_") for k in c.keys()))
with_eidolons = sum(1 for c in chars.values() if isinstance(c, dict) and any(k.startswith("技能_星魂") or (k.startswith("星魂") and k[2:].isdigit()) for k in c.keys()))
with_hp = sum(1 for c in chars.values() if isinstance(c, dict) and "80生命值" in c)
with_name = sum(1 for c in chars.values() if isinstance(c, dict) and c.get("名称", ""))

cones = data.get("light_cones", {})
relics = data.get("relic_sets", {})

print(f"=== Wiki 数据增强检查 ===")
print(f"角色总数: {total}")
print(f"有名称: {with_name}")
print(f"有 _stats: {with_stats}")
print(f"有技能数据 (prefix 技能_): {with_skills}")
print(f"有星魂数据: {with_eidolons}")
print(f"有80生命值: {with_hp}")
print(f"光锥总数: {len(cones)}")
print(f"遗器总数: {len(relics)}")

# 样本: 绯英
for name in ["绯英", "阿格莱雅", "希儿"]:
    c = chars.get(name)
    if isinstance(c, dict):
        keys = list(c.keys())
        stats_info = ""
        if "_stats" in c:
            stats_info = f" stats={c['_stats']}"
        hp_keys = [k for k in keys if "生命" in k][:3]
        skill_keys = [k for k in keys if k.startswith("技能_")][:5]
        print(f"\n[{name}] {len(keys)} keys{stats_info}")
        print(f"  生命相关: {hp_keys}")
        if skill_keys:
            print(f"  技能: {skill_keys}")
        if c.get("星魂1") or c.get("技能_星魂1"):
            print(f"  星魂1: {c.get('星魂1', '') or c.get('技能_星魂1', '')[:40]}")
    else:
        print(f"\n[{name}] NOT FOUND or invalid type: {type(c).__name__}")

# 检查哪些有80级生命值但无_stats
no_stats_with_hp = sum(1 for c in chars.values() if isinstance(c, dict) and "80生命值" in c and "_stats" not in c)
no_hp_with_stats = sum(1 for c in chars.values() if isinstance(c, dict) and "_stats" in c and "80生命值" not in c)
print(f"\n有80生命值但无_stats: {no_stats_with_hp}")
print(f"有_stats但无80生命值: {no_hp_with_stats}")
