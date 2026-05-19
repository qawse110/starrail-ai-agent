#!/usr/bin/env python3
"""检查遗器Wiki数据格式"""
import json

with open("app/src/main/assets/wiki_data.json") as f:
    data = json.load(f)

relics = data.get("relic_sets", {})
print(f"遗器总数: {len(relics)}\n")

# 查看前3个遗器的全部字段
for title in list(relics.keys())[:3]:
    r = relics[title]
    print(f"[{title}]")
    print(f"  字段数: {len(r)}")
    for k, v in sorted(r.items()):
        if k == "名称":
            print(f"  名称 = {v!r}")
        elif k == "稀有度":
            print(f"  稀有度 = {v!r}")
        elif k == "类别":
            print(f"  类别 = {v!r}")
        elif k in ("套装效果2", "套装效果4"):
            print(f"  {k} = {str(v)[:100]!r}")
        elif k in ("_stats",):
            print(f"  {k} = {v}")
    # 汇总所有字段
    print(f"  所有keys: {list(r.keys())}\n")

# 所有遗器可能的字段
all_keys = set()
for r in relics.values():
    all_keys.update(r.keys())
print(f"所有可能的字段: {sorted(all_keys)}")

# 统计分类
relic_count = sum(1 for r in relics.values() if "4" in r.get("类别",""))
orb_count = sum(1 for r in relics.values() if "球" in r.get("类别","") or "位面" in r.get("类别",""))
rope_count = sum(1 for r in relics.values() if "绳" in r.get("类别",""))
print(f"\n遗器套: {relic_count}")
print(f"位面球: {orb_count}")
print(f"连结绳: {rope_count}")