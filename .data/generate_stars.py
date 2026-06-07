#!/usr/bin/env python3
"""
BSC5 + IAU 星表 → stars.json 生成脚本
从 BSC5 提取位置/星等/颜色，IAU 有名称则用 IAU 名称，否则用 HR 编号
输出对齐 stars.json 格式

用法: python generate_stars.py
输出: .data/stars_generated.json
"""

import csv
import json
import math
import os
import re
from dataclasses import dataclass

# ==================== 配置 ====================
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BSC5_PATH = os.path.join(SCRIPT_DIR, 'bsc5', 'catalog')
IAU_PATH = os.path.join(SCRIPT_DIR, 'IAU-Catalog of Star Names (always up to date).csv')
OUTPUT_NAMED = os.path.join(SCRIPT_DIR, 'stars_named.json')
OUTPUT_NUMBERED = os.path.join(SCRIPT_DIR, 'stars_numbered.json')
MAG_LIMIT = 6.5


# ==================== 解析函数 ====================

def parse_ra(ra_str: str) -> float | None:
    """BSC5 赤经 HHMMSS.S → 弧度"""
    s = ra_str.strip()
    if len(s) < 7:
        return None
    try:
        h, m = int(s[0:2]), int(s[2:4])
        sec = float(s[4:])
        return (h + m / 60 + sec / 3600) * 15 * math.pi / 180
    except (ValueError, IndexError):
        return None


def parse_dec(dec_str: str) -> float | None:
    """BSC5 赤纬 sDDMMSS → 弧度"""
    s = dec_str.strip()
    if len(s) < 7:
        return None
    try:
        sign = -1 if s[0] == '-' else 1
        d, m = int(s[1:3]), int(s[3:5])
        sec = float(s[5:7])
        return sign * (d + m / 60 + sec / 3600) * math.pi / 180
    except (ValueError, IndexError):
        return None


def radec_to_xyz(ra_rad: float, dec_rad: float) -> tuple[float, float, float]:
    """赤道坐标 → 单位球面 Cartesian (Y=北天极)"""
    cd = math.cos(dec_rad)
    return (cd * math.cos(ra_rad), math.sin(dec_rad), cd * math.sin(ra_rad))


# ==================== B-V → 颜色算法 ====================
#
#  提供三种模式，在 init() 中通过 COLOR_MODE 切换：
#
#  "artistic"    — 艺术化增强（默认）: 拉大 B-V 对比度 ×1.4，蓝更蓝/红更红
#  "piecewise"   — viewer.html 原始分段线性: 视觉友好，保留作为参考
#  "blackbody"   — 物理黑体辐射 → sRGB: 最接近真实但饱和度低（Ballesteros 2012 T_eff + Planckian locus）
#
#  物理准确性说明:
#  - 真实恒星的 sRGB 颜色在 Planckian 轨迹上非常接近白色
#    （例如天狼星 ~10000K 在 sRGB 中接近(203,222,255)，肉眼几乎看不出蓝色）
#  - "piecewise" 和 "artistic" 都人为增强了饱和度以便视觉区分
#  - 参考: Ballesteros (2012) EPL 97 34008; Planckian locus 近似采用 Krystek (1985)
#
COLOR_MODE = "artistic"  # "artistic" | "piecewise" | "blackbody"

# ---- 黑体辐射物理参数 ----
_BV_TEFF_A = 0.92          # Ballesteros 2012 系数
_BV_TEFF_B = 1.7
_BV_TEFF_C = 0.62
_ARTISTIC_BV_STRETCH = 1.4  # 艺术模式 B-V 拉伸倍数（>1 增强对比度）


def _bv_to_teff(bv: float) -> float:
    """B-V → 有效温度 (K)  参考: Ballesteros, F. (2012) EPL 97, 34008"""
    return 4600.0 * (1.0 / (_BV_TEFF_A * bv + _BV_TEFF_B)
                     + 1.0 / (_BV_TEFF_A * bv + _BV_TEFF_C))


def _planck_xy(T: float) -> tuple[float, float]:
    """色温 T(K) → CIE 1931 xy 色度 (Planckian locus)
    采用分段有理逼近，参考 Krystek (1985) 及 Wyszecki & Stiles 拟合。"""
    if T < 1667:
        T = 1667.0
    if T < 4000:
        x = (-0.2661239e9 / T**3 - 0.2343589e6 / T**2
             + 0.8776956e3 / T + 0.179910)
    else:
        x = (-3.0258469e9 / T**3 + 2.1070379e6 / T**2
             + 0.2226347e3 / T + 0.240390)
    y = -3.0 * x * x + 2.870 * x - 0.275
    return x, y


def _xy_to_linear_srgb(x: float, y: float) -> tuple[float, float, float]:
    """CIE xy (D65, Y=1) → 线性 sRGB，归一化 max=1."""
    Y = 1.0
    X = x / y * Y
    Z = (1.0 - x - y) / y * Y
    r =  3.2406 * X - 1.5372 * Y - 0.4986 * Z
    g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z
    b =  0.0557 * X - 0.2040 * Y + 1.0570 * Z
    m = max(r, g, b)
    if m > 0:
        r /= m; g /= m; b /= m
    return r, g, b


def _srgb_gamma(c: float) -> float:
    """线性 → sRGB gamma"""
    return 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1.0 / 2.4)) - 0.055


def _rgb_to_argb(r: float, g: float, b: float) -> int:
    """0-1 RGB → ARGB int"""
    def _b(v): return int(round(max(0, min(1, v)) * 255))
    return (0xFF << 24) | (_b(r) << 16) | (_b(g) << 8) | _b(b)


def _blackbody_srgb(bv: float) -> tuple[float, float, float]:
    """黑体辐射: B-V → Teff → Planckian xy → 线性 sRGB → gamma → 归一化 RGB"""
    T = _bv_to_teff(bv)
    x, y = _planck_xy(T)
    r, g, b = _xy_to_linear_srgb(x, y)
    r = _srgb_gamma(max(0, min(1, r)))
    g = _srgb_gamma(max(0, min(1, g)))
    b = _srgb_gamma(max(0, min(1, b)))
    # 归一化使最亮通道=1.0，避免变暗
    m = max(r, g, b)
    if m > 0:
        r /= m; g /= m; b /= m
    return r, g, b


def _piecewise_srgb(bv: float) -> tuple[float, float, float]:
    """viewer.html 原始分段线性映射: B-V → 心理色"""
    t = max(-0.4, min(2.0, bv))
    if t <= 0.0:
        f = (t + 0.4) / 0.4
        r, g, b = 0.62 + f * 0.38, 0.72 + f * 0.28, 0.88 + f * 0.12
    elif t <= 0.5:
        f = t / 0.5
        r, g, b = 1.0, 1.0 - f * 0.28, 1.0 - f * 0.88
    elif t <= 1.0:
        f = (t - 0.5) / 0.5
        r, g, b = 1.0, 0.72 - f * 0.30, 0.12 - f * 0.10
    elif t <= 1.6:
        f = (t - 1.0) / 0.6
        r, g, b = 1.0 - f * 0.25, 0.42 - f * 0.30, 0.02
    else:
        f = (t - 1.6) / 0.4
        r, g, b = 0.75 - f * 0.15, 0.12 - f * 0.08, 0.02
    return r, g, b


def bv_to_argb(bv: float | None) -> int:
    """B-V 色指数 → ARGB 整数。由 COLOR_MODE 控制算法。"""
    if bv is None or math.isnan(bv):
        return _rgb_to_argb(0.88, 0.88, 0.94)

    if COLOR_MODE == "blackbody":
        # --- 物理模式: Planckian 黑体辐射 → sRGB ---
        r, g, b = _blackbody_srgb(bv)
    elif COLOR_MODE == "artistic":
        # --- 艺术模式: 拉伸 B-V 后走 piecewise，蓝更蓝/红更红 ---
        bv_stretched = bv * _ARTISTIC_BV_STRETCH
        r, g, b = _piecewise_srgb(bv_stretched)
    else:
        # --- 标准模式: viewer.html 原始分段线性 ---
        r, g, b = _piecewise_srgb(bv)

    return _rgb_to_argb(r, g, b)


# ==================== 数据加载 ====================

def load_iau_names() -> dict[int, str]:
    """IAU CSV → {HR编号: 星名}"""
    hr_to_name: dict[int, str] = {}
    print(f"📖 加载 IAU: {IAU_PATH}")
    try:
        with open(IAU_PATH, encoding='utf-8-sig') as f:
            for row in csv.DictReader(f):
                name = row.get('proper names', '').strip()
                m = re.search(r'HR\s+(\d+)', row.get('Designation', ''))
                if name and m:
                    hr_to_name.setdefault(int(m[1]), name)
    except FileNotFoundError:
        print("  ⚠ IAU CSV 未找到")
    print(f"  ✓ {len(hr_to_name)} 个 IAU 命名恒星")
    return hr_to_name


@dataclass(slots=True)
class Star:
    hr: int
    x: float; y: float; z: float
    vmag: float
    color: int


def parse_bsc5() -> list[Star]:
    """解析 BSC5 固定宽格式（与 viewer.html 同款列位置）"""
    print(f"📖 解析 BSC5: {BSC5_PATH}")
    stars: list[Star] = []
    skipped = 0

    with open(BSC5_PATH, encoding='utf-8', errors='replace') as f:
        for line in f:
            line = line.rstrip('\n\r')
            if len(line) < 114:
                skipped += 1
                continue
            try:
                hr = int(line[0:4])
                ra = parse_ra(line[75:83])
                dec = parse_dec(line[83:90])
                if ra is None or dec is None:
                    skipped += 1
                    continue
                x, y, z = radec_to_xyz(ra, dec)
                vmag = float(line[102:107])
                bv_str = line[109:114].strip()
                bv = float(bv_str) if bv_str else None
            except (ValueError, IndexError):
                skipped += 1
                continue

            stars.append(Star(hr, x, y, z, vmag, bv_to_argb(bv)))

    print(f"  ✓ {len(stars)} 颗星 (跳过 {skipped})")
    return stars


def build_output(stars: list[Star], hr_to_name: dict[int, str]) -> tuple[dict, dict]:
    """拆分为两个列表：IAU 命名的 + HR 编号的，均按星等排序"""
    named, numbered = [], []

    for s in stars:
        if s.vmag > MAG_LIMIT:
            continue
        entry = {
            'name': '',
            'position': [round(s.x, 10), round(s.y, 10), round(s.z, 10)],
            'Vmag': round(s.vmag, 2),
            'color': s.color,
        }
        name = hr_to_name.get(s.hr)
        if name:
            entry['name'] = name
            named.append(entry)
        else:
            entry['name'] = f"HR {s.hr}"
            numbered.append(entry)

    named.sort(key=lambda s: s['Vmag'])
    numbered.sort(key=lambda s: s['Vmag'])
    print(f"\n  IAU 命名: {len(named)}   HR 编号: {len(numbered)}")
    return {'Stars': named}, {'Stars': numbered}


# ==================== 入口 ====================

def main():
    print("=" * 50)
    print("  BSC5 + IAU → stars_generated.json")
    print("=" * 50)

    hr_to_name = load_iau_names()
    stars = parse_bsc5()
    named, numbered = build_output(stars, hr_to_name)

    for path, data in [(OUTPUT_NAMED, named), (OUTPUT_NUMBERED, numbered)]:
        with open(path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent='\t', ensure_ascii=False)
        s = data['Stars']
        if s:
            print(f"✅ {path}  ({len(s)} 颗 | {s[0]['name']} … {s[-1]['name']})")


if __name__ == '__main__':
    main()
