#!/usr/bin/env python3
"""汇总 wiki_data.json 的数据完整度，便于同步后快速检查。"""
import json
import sys

JSON_PATH = "app/src/main/assets/wiki_data.json"


def main():
    if len(sys.argv) > 1:
        json_path = sys.argv[1]
    else:
        json_path = JSON_PATH

    with open(json_path, encoding="utf-8") as f:
        data = json.load(f)

    chars = data.get("characters", {})
    cones = data.get("light_cones", {})
    relics = data.get("relic_sets", {})

    print("=== Wiki 数据完整度检查 ===\n")

    print(f"角色条目: {len(chars)}")
    char_with_name = sum(1 for c in chars.values() if isinstance(c, dict) and c.get("名称", "").strip())
    char_with_path = sum(1 for c in chars.values() if isinstance(c, dict) and c.get("命途", "").strip())
    char_with_element = sum(1 for c in chars.values() if isinstance(c, dict) and c.get("元素属性", "").strip())
    char_with_stats = sum(1 for c in chars.values() if isinstance(c, dict) and c.get("_stats"))
    char_with_skill = sum(1 for c in chars.values() if isinstance(c, dict) and (
        c.get("技能_名称", "").strip() or any(c.get(f"技能_{k}", "").strip() for k in ("普攻", "战技", "终结技", "天赋", "秘技"))
    ))
    char_with_eidolon = sum(1 for c in chars.values() if isinstance(c, dict) and any(
        c.get(f"技能_星魂{i}", "").strip() or c.get(f"星魂{i}", "").strip() for i in range(1, 7)
    ))
    print(f"  有名称: {char_with_name}")
    print(f"  有命途: {char_with_path}")
    print(f"  有属性: {char_with_element}")
    print(f"  有 _stats: {char_with_stats}")
    print(f"  有技能: {char_with_skill}")
    print(f"  有星魂: {char_with_eidolon}")

    print(f"\n光锥条目: {len(cones)}")
    cone_with_name = sum(1 for c in cones.values() if isinstance(c, dict) and c.get("名称", "").strip())
    cone_with_path = sum(1 for c in cones.values() if isinstance(c, dict) and c.get("命途", "").strip())
    cone_with_rarity = sum(1 for c in cones.values() if isinstance(c, dict) and c.get("稀有度", "").strip())
    cone_with_skill = sum(1 for c in cones.values() if isinstance(c, dict) and c.get("技能名称", "").strip())
    print(f"  有名称: {cone_with_name}")
    print(f"  有命途: {cone_with_path}")
    print(f"  有稀有度: {cone_with_rarity}")
    print(f"  有技能: {cone_with_skill}")

    print(f"\n遗器套装条目: {len(relics)}")
    relic_with_name = sum(1 for r in relics.values() if isinstance(r, dict) and r.get("名称", "").strip())
    relic_with_category = sum(1 for r in relics.values() if isinstance(r, dict) and r.get("类别", "").strip())
    relic_with_effect = sum(1 for r in relics.values() if isinstance(r, dict) and (
        r.get("两件套效果", "").strip() or r.get("四件套效果", "").strip()
    ))
    print(f"  有名称: {relic_with_name}")
    print(f"  有类别: {relic_with_category}")
    print(f"  有效果: {relic_with_effect}")


if __name__ == "__main__":
    main()