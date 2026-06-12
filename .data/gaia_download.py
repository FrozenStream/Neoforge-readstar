#!/usr/bin/env python3
"""
通过 Gaia TAP API 下载亮星星表（含精确色温数据）
依赖: astroquery (pip install astroquery)

用法: .venv/Scripts/python gaia_download.py
输出: .data/gaia_bright_with_teff.vot
"""

import os
from astroquery.gaia import Gaia

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT = os.path.join(SCRIPT_DIR, 'gaia_bright_with_teff_vmag10.vot')

QUERY = """
SELECT source_id, ra, dec,
       phot_g_mean_mag, phot_bp_mean_mag, phot_rp_mean_mag, bp_rp,
       teff_gspphot, ag_gspphot, logg_gspphot,
       parallax, pmra, pmdec, radial_velocity
FROM gaiadr3.gaia_source
WHERE phot_g_mean_mag < 10.0
"""

def main():
    print("=" * 55)
    print("  Gaia DR3 TAP 查询: G < 10.0 + teff/BP/RP")
    print("=" * 55)

    print("\n📡 提交异步查询...")
    job = Gaia.launch_job_async(QUERY, dump_to_file=True, output_format="votable")
    print(f"   任务 URL: {job.remoteLocation}")

    print("⏳ 等待结果... (可能需要 1-3 分钟)")
    results = job.get_results()

    print(f"   ✓ {len(results)} 行, {len(results.columns)} 列")
    print(f"   列: {results.colnames}")

    results.write(OUTPUT, format="votable", overwrite=True)
    print(f"\n✅ 保存到: {OUTPUT}")


if __name__ == '__main__':
    main()
