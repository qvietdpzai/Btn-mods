#!/usr/bin/env python3
"""Migrate EconomyShopGUI -> Btn-shop/shops.yml

Hien trang ESGU:
  sections/*.yml            : tile menu cap 1 (title/icon/slot)
  shops/*.yml               : hoac shop phang (pages>items) HOAC submenu
                              (items co truong 'section:' tro toi thu muc con)
  shops/<Parent>/<sub>.yml  : shop that cua subsection

Ket qua: shops.yml cua Btn-shop - moi section la 1 danh sach phang item,
plugin tu phan trang. Subsection thanh section rieng trong menu chinh.
"""
import os, re, glob

SRC = "/tmp"
OUT = "/tmp/shops.btnshop.yml"

def clean(s):
    if s is None: return None
    s = re.sub(r"<#[0-9A-Fa-f]{6}>", "", s)      # mau hex
    s = re.sub(r"</?[a-zA-Z]+>", "", s)           # tag dang <bold>
    s = re.sub(r"&[0-9a-fk-or]", "", s)           # legacy color
    s = re.sub(r"[_]+", " ", s)                    # gach chan -> cach
    s = re.sub(r"\bZ\b", "", s)
    s = re.sub(r"\s+", " ", s)
    return s.strip(" <>")

def fmtnum(v):
    if v is None: return -1
    if float(v) == int(float(v)): return str(int(float(v)))
    return ("%g" % float(v))

def kv(line):
    s = line.strip()
    if ":" not in s: return None
    k, v = s.split(":", 1)
    return k.strip(), v.strip().strip("'\"")

def shop_items(path):
    """Doc pages>pageN>items. Tra ve (items, is_submenu)."""
    items, cur, is_sub = [], None, False
    with open(path, errors="ignore") as f:
        for line in f:
            if not line.strip() or line.strip().startswith("#"): continue
            ind = len(line) - len(line.lstrip(" "))
            s = line.strip()
            if s == "items:":
                cur = None; continue
            if ind == 6 and s.endswith(":"):
                if cur: items.append(cur)
                cur = {}; continue
            if cur is not None and ind >= 8:
                p = kv(s)
                if not p: continue
                k, v = p
                cur[k] = v
                if k == "section": is_sub = True
    if cur: items.append(cur)
    return items, is_sub

def num(s, d=None):
    try: return float(s)
    except (TypeError, ValueError): return d

# ---- 1. thu thap metadata tile cap 1 tu sections/*.yml ----
top_meta = {}
for p in glob.glob(os.path.join(SRC, "*.yml")):
    txt = open(p, errors="ignore").read()
    if "item-layout" not in txt and "slot:" not in txt: continue
    t = re.search(r"^title:\s*'?([^'\n]*)'", txt, re.M) or re.search(r'^title:\s*"([^"]*)"', txt, re.M)
    icon = re.search(r"^\s*material:\s*([A-Z_0-9]+)", txt, re.M)
    disp = re.search(r"displayname:\s*'?([^'\n]*)", txt)
    sid = os.path.basename(p)[:-4]
    m = {}
    if t: m["title"] = clean(t.group(1))
    if icon: m["icon"] = icon.group(1)
    if disp and clean(disp.group(1)): m["icon-name"] = clean(disp.group(1))
    if m: top_meta[sid] = m

# ---- 2. xu ly shop files ----
sections = {}   # sid -> {title, icon, icon-name, items:[...]}
def section(sid, title=None, icon=None, icon_name=None):
    s = sections.setdefault(sid, {"title": title or sid.title(),
                                  "icon": icon or "CHEST",
                                  "icon-name": icon_name, "items": []})
    if title: s["title"] = title
    if icon: s["icon"] = icon
    if icon_name: s["icon-name"] = icon_name
    return s

def add_items(sid, items):
    s = section(sid)
    have = {i["material"] for i in s["items"]}
    n = 0
    for it in items:
        mat = it.get("material", "")
        if not re.match(r"^[A-Z_0-9]+$", mat) or mat in have: continue
        e = {"material": mat, "buy": num(it.get("buy"), -1), "sell": num(it.get("sell"), -1)}
        ss = num(it.get("stack-size"))
        if ss and ss > 1: e["stack-size"] = int(ss)
        s["items"].append(e); have.add(mat); n += 1
    return n

# 2a. shop phang (flat: misc, music, resources, storage, Z_EverythingElse)
for p in glob.glob(os.path.join(SRC, "*.yml")):
    sid = os.path.basename(p)[:-4]
    if sid.startswith("shop_") or sid.startswith("shops."): continue
    items, is_sub = shop_items(p)
    if is_sub or not items: continue
    meta = top_meta.get(sid, {})
    add_items(sid, items)
    if meta:
        s = section(sid)
        s["title"] = meta.get("title", s["title"])
        s["icon"] = meta.get("icon", s["icon"])
        s["icon-name"] = meta.get("icon-name")

# 2b. submenu flat (building, combat, decoration, farming, magic, mobs)
#     -> moi tile la mot section lay item tu shops/<Parent>/<tile.section>.yml
for p in glob.glob(os.path.join(SRC, "*.yml")):
    parent = os.path.basename(p)[:-4]
    tiles, is_sub = shop_items(p)
    if not is_sub: continue
    for t in tiles:
        sub = t.get("section")
        mat = t.get("material", "")
        if not sub or not re.match(r"^[A-Z_0-9]+$", mat): continue
        # tim file shop that: shop_<Parent>_<sub>.yml (khong phan biet hoa/thuong)
        cand = None
        for c in glob.glob(os.path.join(SRC, "shop_*.yml")):
            n = os.path.basename(c)[len("shop_"):-4]   # Building_stone
            p2, _, s2 = n.partition("_")
            if p2.lower() == parent.lower() and s2.lower() == sub.lower():
                cand = c; break
        title = clean(t.get("displayname")) or sub.title()
        section(sub.lower(), title=title, icon=mat, icon_name=title)
        if cand and os.path.exists(cand):
            sub_items, _ = shop_items(cand)
            add_items(sub.lower(), sub_items)

# ---- 3. xuat ----
slots = [10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43]
out = ["# Btn-shop shops.yml (migrate tu EconomyShopGUI)",
       "# Sua file nay roi /btnshop reload",
       "sections:"]
total = 0
n = 0
for sid in sorted(sections, key=lambda k: -len(sections[k]["items"])):
    s = sections[sid]
    if not s["items"]: continue
    total += len(s["items"])
    out.append(f"  {sid}:")
    out.append(f"    title: '{s['title']}'")
    out.append(f"    icon: {s['icon']}")
    out.append(f"    icon-name: '{(s['icon-name'] or s['title'])}'")
    out.append(f"    slot: {slots[n % len(slots)]}")
    n += 1
    out.append(f"    items:  # {len(s['items'])}")
    for e in s["items"]:
        out.append(f"      - material: {e['material']}")
        out.append(f"        buy: {fmtnum(e['buy'])}")
        out.append(f"        sell: {fmtnum(e['sell'])}")
        if "stack-size" in e: out.append(f"        stack-size: {e['stack-size']}")
with open(OUT, "w") as f: f.write("\n".join(out) + "\n")

print(f"OK: {n} sections, {total} items -> {OUT}")
for sid in sorted(sections, key=lambda k: -len(sections[k]["items"])):
    if sections[sid]["items"]:
        print(f"  {sid}: {len(sections[sid]['items'])} items (icon {sections[sid]['icon']}, title '{sections[sid]['title']}')")
