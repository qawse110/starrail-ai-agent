#!/usr/bin/env python3
"""
星穹铁道 Wiki 数据同步脚本
从 bilibili Wiki MediaWiki API 下载角色/光锥/遗器数据
输出 JSON 到指定路径，供 Android 应用读取

用法:
  python3 tools/sync_wiki_data.py [输出路径]
  
默认输出到: app/src/main/assets/wiki_data.json
"""
import json
import os
import sys
import time
import urllib.request
import urllib.parse
import urllib.error

API_BASE = "https://wiki.biligame.com/sr/api.php"
USER_AGENT = "StarRailAI-Agent/1.0 (Python)"
REQUEST_DELAY = 0.5  # 500ms 限流

CATEGORIES = [
    ("角色", "characters"),
    ("光锥", "light_cones"),
    ("遗器", "relic_sets"),
]


def api_get(params):
    """调用 MediaWiki API（带重试和响应格式校验）"""
    query_string = urllib.parse.urlencode(params)
    url = f"{API_BASE}?{query_string}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                body = resp.read().decode("utf-8")
                # 检查是否是HTML（非JSON响应）
                if body.strip().startswith("<!") or body.strip().startswith("<"):
                    raise Exception(f"返回HTML而非JSON (可能被限流或服务不可用)")
                return json.loads(body)
        except Exception as e:
            print(f"  API重试 {attempt+1}/3: {e}", file=sys.stderr)
            if attempt < 2:
                time.sleep(3 * (attempt + 1))  # 增加等待时间
    raise Exception(f"API请求失败: {url}")


def fetch_category_members(category):
    """获取分类下所有页面标题"""
    members = []
    cmcontinue = None
    max_pages = 500
    
    while max_pages > 0:
        max_pages -= 1
        params = {
            "action": "query",
            "list": "categorymembers",
            "cmtitle": f"Category:{category}",
            "cmlimit": "max",
            "format": "json",
        }
        if cmcontinue:
            params["cmcontinue"] = cmcontinue
        
        data = api_get(params)
        query = data.get("query", {})
        items = query.get("categorymembers", [])
        for item in items:
            if item.get("ns") == 0:
                members.append(item["title"])
        
        cont = data.get("continue", {})
        cmcontinue = cont.get("cmcontinue")
        if not cmcontinue:
            break
        time.sleep(REQUEST_DELAY)
    
    return members


def fetch_page_content(title):
    """获取页面内容"""
    params = {
        "action": "query",
        "prop": "revisions",
        "rvprop": "content",
        "format": "json",
        "titles": title,
    }
    data = api_get(params)
    pages = data.get("query", {}).get("pages", {})
    for page_id, page in pages.items():
        if page_id == "-1":
            continue
        revisions = page.get("revisions", [])
        if revisions:
            wikitext = revisions[0].get("*", "") or revisions[0].get("slots", {}).get("main", {}).get("*", "")
            meta = extract_template_fields(wikitext)
            meta["title"] = title
            meta["page_id"] = page.get("pageid", 0)
            return meta
    return None


def extract_template_fields(wikitext):
    """从 wiki 模板提取字段（增强版：保留全部数据，解析属性数值）"""
    import re
    meta = {}
    
    for line in wikitext.split("\n"):
        line = line.strip()
        if line.startswith("|") and "=" in line:
            parts = line[1:].split("=", 1)
            key = parts[0].strip()
            value = parts[1].strip() if len(parts) > 1 else ""
            # 保留原始值，仅做基本清理
            meta[key] = clean_wiki_text_for_value(value)
    
    # 如果没提取到，尝试用正则
    if not meta:
        # 从 {{角色图鉴 模板中提取
        m = re.search(r'\{\{角色图鉴\s*\n(.*?)\n\}\}', wikitext, re.DOTALL)
        if m:
            for line in m.group(1).split("\n"):
                line = line.strip()
                if line.startswith("|") and "=" in line:
                    parts = line[1:].split("=", 1)
                    key = parts[0].strip()
                    value = parts[1].strip() if len(parts) > 1 else ""
                    meta[key] = clean_wiki_text_for_value(value)
    
    # 提取角色/技能模板（多种变体）
    skill_templates = [
        r'\{\{角色/技能\s*\n(.*?)\n\}\}',
        r'\{\{角色/技能\|(.*?)\}\}',
        r'\{\{技能\s*\n(.*?)\n\}\}',
        r'\{\{技能\|(.*?)\}\}',
    ]
    for pattern in skill_templates:
        skill_m = re.search(pattern, wikitext, re.DOTALL)
        if skill_m:
            skill_text = skill_m.group(1)
            skill_meta = {}
            for line in skill_text.split("\n"):
                line = line.strip()
                if line.startswith("|") and "=" in line:
                    parts = line[1:].split("=", 1)
                    key = parts[0].strip()
                    value = parts[1].strip() if len(parts) > 1 else ""
                    skill_meta["技能_" + key] = clean_wiki_text_for_value(value)
                elif "=" in line and not line.startswith("{{"):
                    # 内联格式: key=value
                    parts = line.split("=", 1)
                    key = parts[0].strip()
                    value = parts[1].strip() if len(parts) > 1 else ""
                    skill_meta["技能_" + key] = clean_wiki_text_for_value(value)
            meta.update(skill_meta)
            break  # 找到一个就够
    
    # 解析基础属性
    _parse_stats(meta)
    
    return meta


def clean_wiki_text_for_value(text):
    """清理 wiki 文本但保留完整内容（不再截断200字）"""
    import re
    # 移除注释
    text = re.sub(r'<!--.*?-->', '', text, flags=re.DOTALL)
    # 简化颜色模板 {{颜色|类型|文字}} → 文字
    text = re.sub(r'\{\{颜色\|[^|]*\|([^}]*)\}\}', r'\1', text)
    # 移除其他模板（保留模板名）
    text = re.sub(r'\{\{[^}]*\}\}', '', text)
    # 移除HTML标签
    text = re.sub(r'<br\s*/?>', '\n', text, flags=re.IGNORECASE)
    text = re.sub(r'<[^>]+>', '', text)
    # 简化Wiki链接
    text = re.sub(r'\[\[([^|]+)\|([^\]]+)\]\]', r'\2', text)
    text = re.sub(r'\[\[([^\]]+)\]\]', r'\1', text)
    # 移除粗斜体标记
    text = text.replace("'''", "").replace("''", "")
    return text.strip()


STAT_KEYS = {
    '速度': 'spd', '生命值': 'hp_lv1', '80生命值': 'hp_lv80',
    '攻击力': 'atk_lv1', '80攻击力': 'atk_lv80',
    '防御力': 'def_lv1', '80防御力': 'def_lv80',
}


def _parse_stats(meta):
    """解析基础属性字段"""
    stats = {}
    for wiki_key, stat_key in STAT_KEYS.items():
        if wiki_key in meta:
            val = meta[wiki_key]
            try:
                stats[stat_key] = float(val)
            except (ValueError, TypeError):
                pass
    if stats:
        meta['_stats'] = stats


def main():
    # 获取输出路径
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)  # tools/../ = 项目根
    
    if len(sys.argv) > 1:
        output_path = sys.argv[1]
    else:
        output_path = os.path.join(project_root, "app", "src", "main", "assets", "wiki_data.json")
    
    print(f"Wiki 数据同步工具")
    print(f"API: {API_BASE}")
    print(f"输出: {output_path}")
    print()
    
    all_data = {
        "sync_time": int(time.time() * 1000),
        "sync_tool": "sync_wiki_data.py",
    }
    
    for cat_name, output_key in CATEGORIES:
        print(f"[1/2] 获取{cat_name}列表...")
        titles = fetch_category_members(cat_name)
        print(f"     共 {len(titles)} 个{cat_name}")
        
        result = {}
        for i, title in enumerate(titles):
            progress = f"[{i+1}/{len(titles)}]"
            print(f"  {progress} 下载: {title}")
            
            try:
                content = fetch_page_content(title)
                if content:
                    result[title] = content
            except Exception as e:
                print(f"    ⚠️ 错误: {e}", file=sys.stderr)
            
            time.sleep(REQUEST_DELAY)
        
        all_data[output_key] = result
        all_data[f"{output_key}_count"] = len(result)
        print(f"   ✅ {cat_name}: {len(result)} 个完成")
        print()
    
    # 写入文件
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(all_data, f, ensure_ascii=False, indent=2)
    
    total = sum(all_data.get(f"{k}_count", 0) for _, k in CATEGORIES)
    file_size = os.path.getsize(output_path) / 1024
    print(f"✅ 同步完成！共 {total} 个条目，保存至 {output_path} ({file_size:.0f}KB)")


if __name__ == "__main__":
    main()