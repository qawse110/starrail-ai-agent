#!/usr/bin/env python3
"""解析Wiki页面原始wikitext，找到所有技能模板"""
import json, re, sys, urllib.request, urllib.parse

API_BASE = "https://wiki.biligame.com/sr/api.php"
UA = "StarRailAI-Agent/1.0"

def api_get(params):
    qs = urllib.parse.urlencode(params)
    url = f"{API_BASE}?{qs}"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))

# 获取绯英页面原始内容
title = sys.argv[1] if len(sys.argv) > 1 else "绯英"
data = api_get({"action":"query","prop":"revisions","rvprop":"content","format":"json","titles":title})
pages = data.get("query",{}).get("pages",{})
for pid, page in pages.items():
    if pid == "-1": continue
    revs = page.get("revisions",[])
    if revs:
        wt = revs[0].get("*","")
        
        # 找所有 {{...}} 模板
        templates = re.findall(r'\{\{([^|}]+)[|\n]', wt)
        print(f"=== {title} 页面包含的模板 ===")
        for t in templates:
            print(f"  {{{{{t}")
        
        # 找与技能相关的模板
        print(f"\n=== 与技能相关的模板 ===")
        skill_related = []
        for m in re.finditer(r'\{\{(角色/技能|技能|角色/技能/[^|}]+)', wt):
            name = m.group(1)
            start = m.start()
            # 找到对应的结束 }}
            depth = 0
            end = start
            for i in range(start, min(start + 20000, len(wt))):
                if wt[i:i+2] == '{{':
                    depth += 1
                elif wt[i:i+2] == '}}':
                    depth -= 1
                    if depth == 0:
                        end = i + 2
                        break
            block = wt[start:end]
            skill_related.append((name, block))
            print(f"\n  模板: {name}")
            print(f"  长度: {len(block)} 字符")
            # 显示前200字符
            print(f"  内容: {block[:200]}")
        
        # 找角色/技能子模板
        print(f"\n=== 角色/技能 子模板列表 ===")
        for m in re.finditer(r'\{\{角色/技能/([^|}]+)', wt):
            print(f"  角色/技能/{m.group(1)}")
        
        # 找所有以技能命名的字段（直接在角色图鉴里的）
        print(f"\n=== 角色图鉴中技能相关字段 ===")
        in_chara = False
        for line in wt.split("\n"):
            line = line.strip()
            if line.startswith("{{角色图鉴"):
                in_chara = True
            if in_chara and line == "}}":
                in_chara = False
            if in_chara and line.startswith("|") and "=" in line:
                key = line[1:].split("=",1)[0].strip()
                if any(k in key for k in ["普攻","战技","终结技","天赋","秘技","技能","欢愉","忆灵"]):
                    val = line[1:].split("=",1)[1].strip()[:80]
                    print(f"  {key} = {val}")
    break