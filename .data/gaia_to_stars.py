#!/usr/bin/env python3
"""
Gaia DR3 VOTable (含 teff) → stars.json 转换
依赖: .venv (astropy + numpy)

颜色策略:
  优先级 1: GSP-Phot 有效温度 → Planckian 黑体辐射 → sRGB (物理色)
  优先级 2: bp_rp 色指数 → 分段近似映射 (回退方案)
  两种模式都支持饱和度增强 (COLOR_BOOST)

用法: .venv/Scripts/python gaia_to_stars.py
"""

import json
import math
import os

import numpy as np
from astropy.table import Table

# ==================== 配置 ====================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
VOT_PATH = os.path.join(SCRIPT_DIR, 'gaia_bright_with_teff_10.vot')
IAU_PATH = os.path.join(SCRIPT_DIR, 'IAU-Catalog of Star Names (always up to date).csv')
OUTPUT_NAMED = os.path.join(SCRIPT_DIR, 'stars_gaia_named_10.json')
OUTPUT_NUMBERED = os.path.join(SCRIPT_DIR, 'stars_gaia_numbered_10.json')
COLOR_BOOST = 1.6   # 饱和度增强 (1.0=物理原色, >1=更鲜艳)


# ==================== 颜色引擎 ====================

def _rgb_to_argb(r: float, g: float, b: float) -> int:
    def _b(v): return int(round(max(0, min(1, v)) * 255))
    return (0xFF << 24) | (_b(r) << 16) | (_b(g) << 8) | _b(b)


def _boost_saturation(r: float, g: float, b: float, factor: float) -> tuple[float, float, float]:
    """增强饱和度: factor=1.0 不变, >1 更鲜艳"""
    if factor == 1.0:
        return r, g, b
    gray = 0.299 * r + 0.587 * g + 0.114 * b
    return tuple(max(0, min(1, gray + factor * (c - gray))) for c in (r, g, b))


# ---- 方案 A: 有效温度 → Planckian 黑体 → sRGB ----

def _teff_to_planck_xy(T: float) -> tuple[float, float]:
    """色温 T(K) → CIE 1931 xy (Planckian locus, Krystek 1985)"""
    T = max(1667, min(50000, T))
    if T < 4000:
        x = (-0.2661239e9 / T**3 - 0.2343589e6 / T**2
             + 0.8776956e3 / T + 0.179910)
    else:
        x = (-3.0258469e9 / T**3 + 2.1070379e6 / T**2
             + 0.2226347e3 / T + 0.240390)
    y = -3.0 * x * x + 2.870 * x - 0.275
    return x, y


def _xy_to_srgb(x: float, y: float) -> tuple[float, float, float]:
    """CIE xy (D65) → 线性 sRGB, 归一化 max=1"""
    Y = 1.0; X = x / y * Y; Z = (1 - x - y) / y * Y
    r =  3.2406 * X - 1.5372 * Y - 0.4986 * Z
    g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z
    b =  0.0557 * X - 0.2040 * Y + 1.0570 * Z
    m = max(r, g, b)
    if m > 0:
        r, g, b = r / m, g / m, b / m
    return max(0, min(1, r)), max(0, min(1, g)), max(0, min(1, b))


def _srgb_gamma(c: float) -> float:
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055


def teff_to_argb(teff: float | None) -> int | None:
    """有效温度 → Planckian sRGB → ARGB (含饱和度增强)"""
    if teff is None or np.isnan(teff) or teff <= 0:
        return None
    x, y = _teff_to_planck_xy(float(teff))
    r, g, b = _xy_to_srgb(x, y)
    r = _srgb_gamma(r); g = _srgb_gamma(g); b = _srgb_gamma(b)
    m = max(r, g, b)
    if m > 0:
        r, g, b = r / m, g / m, b / m
    r, g, b = _boost_saturation(r, g, b, COLOR_BOOST)
    return _rgb_to_argb(r, g, b)


# ---- 方案 B: bp_rp → 分段近似 (回退) ----

def bp_rp_to_argb(bp_rp: float | None) -> int:
    """bp_rp 色指数 → ARGB (回退方案)"""
    if bp_rp is None or np.isnan(bp_rp):
        return _rgb_to_argb(0.88, 0.88, 0.94)

    t = max(-0.5, min(4.0, bp_rp))
    if t <= 0.0:
        f = (t + 0.5) / 0.5
        r, g, b = 0.55 + f * 0.45, 0.68 + f * 0.32, 0.88 + f * 0.12
    elif t <= 0.8:
        f = t / 0.8
        r, g, b = 1.0, 1.0 - f * 0.25, 1.0 - f * 0.85
    elif t <= 1.5:
        f = (t - 0.8) / 0.7
        r, g, b = 1.0, 0.75 - f * 0.35, 0.15 - f * 0.10
    elif t <= 2.5:
        f = (t - 1.5) / 1.0
        r, g, b = 1.0 - f * 0.25, 0.40 - f * 0.28, 0.05 - f * 0.03
    else:
        f = (t - 2.5) / 1.5
        r, g, b = 0.75 - f * 0.20, 0.12 - f * 0.08, 0.02

    r, g, b = _boost_saturation(r, g, b, COLOR_BOOST)
    return _rgb_to_argb(r, g, b)


def compute_color(teff, bp_rp) -> int:
    """选择最佳颜色方案: teff 优先, bp_rp 回退"""
    c = teff_to_argb(teff)
    if c is not None:
        return c
    return bp_rp_to_argb(bp_rp)


# ==================== 坐标转换 ====================

def radec_to_xyz(ra_deg: float, dec_deg: float) -> tuple[float, float, float]:
    ra = math.radians(ra_deg); dec = math.radians(dec_deg)
    cd = math.cos(dec)
    return (cd * math.cos(ra), math.sin(dec), cd * math.sin(ra))


# ==================== 数据加载 ====================

def parse_gaia() -> list[dict]:
    print(f"📖 解析 Gaia: {VOT_PATH}")
    t = Table.read(VOT_PATH)
    stars = []
    teff_ok = 0
    for row in t:
        gmag = float(row['phot_g_mean_mag'])
        ra = float(row['ra']); dec = float(row['dec'])
        x, y, z = radec_to_xyz(ra, dec)
        teff = float(row['teff_gspphot']) if not np.ma.is_masked(row['teff_gspphot']) else None
        bp_rp = float(row['bp_rp']) if not np.ma.is_masked(row['bp_rp']) else None
        color = compute_color(teff, bp_rp)
        if teff is not None:
            teff_ok += 1
        stars.append({
            'source_id': int(row['source_id']),
            'x': x, 'y': y, 'z': z,
            'Vmag': round(gmag, 2),
            'color': color,
        })
    print(f"  ✓ {len(stars)} 颗星 | teff 有效: {teff_ok} ({teff_ok*100//len(stars)}%)")
    return stars


def build_output(stars: list[dict]) -> tuple[dict, dict]:
    # 加载 BSC5 IAU 位置用于交叉匹配
    bsc5_path = os.path.join(SCRIPT_DIR, 'stars_named.json')
    bsc5_positions = []
    if os.path.exists(bsc5_path):
        with open(bsc5_path) as f:
            for s in json.load(f)['Stars']:
                bsc5_positions.append((s['name'], s['position'][0], s['position'][1], s['position'][2]))
    print(f"  📍 {len(bsc5_positions)} 个 BSC5 IAU 位置")

    gaia_xyz = np.array([[s['x'], s['y'], s['z']] for s in stars])
    MATCH_THRESHOLD = math.cos(math.radians(0.5))

    gaia_idx_to_name = {}
    for name, bx, by, bz in bsc5_positions:
        dots = np.dot(gaia_xyz, [bx, by, bz])
        best = int(np.argmax(dots))
        if dots[best] >= MATCH_THRESHOLD:
            gaia_idx_to_name[best] = name

    named, numbered = [], []
    matched_names = set()
    for i, s in enumerate(stars):
        entry = {
            'name': '',
            'position': [round(s['x'], 10), round(s['y'], 10), round(s['z'], 10)],
            'Vmag': s['Vmag'],
            'color': s['color'],
        }
        if i in gaia_idx_to_name:
            entry['name'] = gaia_idx_to_name[i]
            named.append(entry)
            matched_names.add(gaia_idx_to_name[i])
        else:
            entry['name'] = f"Gaia {s['source_id']}"
            numbered.append(entry)

    # ---- 回退: Gaia 缺失的亮星从 BSC5 补入 ----
    bsc5_named_path = os.path.join(SCRIPT_DIR, 'stars_named.json')
    if os.path.exists(bsc5_named_path):
        with open(bsc5_named_path) as f:
            bsc5_named = json.load(f)['Stars']
        fallback = 0
        for s in bsc5_named:
            if s['name'] not in matched_names:
                named.append(s)  # 直接用 BSC5 数据
                fallback += 1
        if fallback:
            print(f"  🔄 BSC5 回退: {fallback} 颗 (Gaia 饱和/缺失)")

    named.sort(key=lambda x: x['Vmag'])
    numbered.sort(key=lambda x: x['Vmag'])
    print(f"  IAU 命名: {len(named)}   Gaia 编号: {len(numbered)}")
    return {'Stars': named}, {'Stars': numbered}


# ==================== 入口 ====================

def main():
    print("=" * 55)
    print(f"  Gaia DR3 + teff → stars.json  (boost={COLOR_BOOST})")
    print("=" * 55)
    stars = parse_gaia()
    named, numbered = build_output(stars)
    for path, data in [(OUTPUT_NAMED, named), (OUTPUT_NUMBERED, numbered)]:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent='\t', ensure_ascii=False)
        s = data['Stars']
        if s:
            print(f"✅ {os.path.basename(path)}  ({len(s)}颗 | {s[0]['name']} … {s[-1]['name']})")


if __name__ == '__main__':
    main()
