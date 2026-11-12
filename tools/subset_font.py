#!/usr/bin/env python3
"""把随包字体裁剪成 UI 实际用到的字符集。

用法（需要 fonttools：pip install fonttools）：
    python tools/subset_font.py <原始字体.ttf> <输出字体.ttf>

为什么要子集化：Google Sans Code 原始文件 260 KB，但我们只用它渲染
数字、拉丁字母和少量符号；中文本来就靠系统字体按字符回退。
裁剪后约 60 KB，且保留可变轴（wght / MONO）。

注意：改 UI 文案时如果引入了新的符号字符，把它加进 EXTRA 里再重新跑一遍，
否则那个字符会回退到系统字体（不会崩，只是字形不一致）。
"""

import sys
import subprocess

# U+00D7 × / U+201C “ / U+201D ” / U+2026 … / U+2192 → / U+2212 −
EXTRA = 'U+00D7,U+201C,U+201D,U+2026,U+2192,U+2212'
RANGES = ['U+0020-007E', 'U+00A0']

UNICODES = ','.join(RANGES + [EXTRA])


def main():
    if len(sys.argv) != 3:
        print(__doc__)
        return 1
    src, dst = sys.argv[1], sys.argv[2]
    cmd = [
        sys.executable, '-m', 'fontTools.subset', src,
        '--output-file=' + dst,
        '--unicodes=' + UNICODES,
        '--layout-features=*',
        '--no-hinting',
        '--desubroutinize',
        '--drop-tables+=DSIG',
        '--name-IDs=*',
    ]
    print(' '.join(cmd[-6:]))
    subprocess.run(cmd, check=True)
    import os
    print(f'{src}: {os.path.getsize(src) / 1024:.0f} KB -> {dst}: {os.path.getsize(dst) / 1024:.0f} KB')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
