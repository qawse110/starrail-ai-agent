#!/usr/bin/env python3
"""检查单个Wiki页面原始内容"""
import json, re, urllib.request, urllib.parse, sys

API_BASE = "https://wiki.biligame.com/sr/api.php"
USER_AGENT = "StarRailAI-Agent/1.0 (Python)"

def api_get(params):
    qs = urllib.parse.urlencode(params)
    url = f"{API_BASE}?{qs}"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        body = resp.read().decode("utf-8")
        if body.strip().startswith("<"):
            raise Exception("HTML response")
        return json.loads(body)

title = sys.argv[1] if len(sys.argv) > 1 else "绯英"
params = {"action":"query","prop":"revisions","rvprop":"content","format":"json","titles":title}
data = api_get(params)
pages = data.get("query",{}).get("pages",{})
for pid, page in pages.items():
    if pid == "-1": continue
    revs = page.get("revisions",[])
    if revs:
        wt = revs[0].get("*","") or revs[0].get("slots",{}).get("main",{}).get("*","")
        # 提取角色/技能模板附近内容
        parts = wt.split("{{角色/技能")
        print(f"=== {title} 页面原始内容 ===")
        print(f"总长度: {len(wt)}")
        print(f"包含 '角色/技能' 模板: {len(parts)-1} 处")
        if len(parts) > 1:
            print("\n=== {{角色/技能 模板内容 (前500字) ===")
            print(parts[1][:500])
        # 查看含"技能"的行
        skill_lines = [l for l in wt.split("\n") if "技能" in l and l.strip().startswith("|")]
        print(f"\n=== 含'技能'的行 (共{len(skill_lines)}行) ===")
        for l in skill_lines[:20]:
            print(f"  {l.strip()[:120]}")
    break
