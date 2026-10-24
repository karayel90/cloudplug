"""Kart logolarini paletli PNG'ye indirger (256 renk, optimize).

Kullanim:
    python tools/optimize-logos.py logos

`tools/build-logos.ps1` bu betigi kendi sonunda cagirir; ayrica elle de
calistirilabilir. Amac kart gorsellerinin agirligini azaltmak: 512x512
seffaf PNG'ler ~45 KB ortalamayla 157 dosyada ~6,9 MB tutuyordu. Logolar
genellikle duz renkli oldugu icin 256 renk kayipsiz sayilir; toplam boyut
yaklasik %85 azalir.

Not: paletli PNG tek bir saydam renk tasiyabildigi icin alfa kenarlari
ikili (0/255) olur. Kart boyutunda bu fark gorunmez.
"""

from __future__ import annotations

import sys
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

from PIL import Image
from PIL.PngImagePlugin import PngInfo

COLORS = 256

# Isaret: ayni dosya ikinci kez nicemlenmesin. Yeniden nicemleme gorseli
# her calistirmada biraz daha bozardi.
MARKER = "cloudplug-optimized"


def optimize(path: Path) -> tuple[int, int]:
    """Tek dosyayi optimize eder; (onceki, sonraki) bayt dondurur."""
    before = path.stat().st_size
    with Image.open(path) as image:
        if image.info.get(MARKER):
            return before, before
        rgba = image.convert("RGBA")
        quantized = rgba.quantize(
            colors=COLORS,
            method=Image.FASTOCTREE,
            dither=Image.FLOYDSTEINBERG,
        )
        meta = PngInfo()
        meta.add_text(MARKER, "1")
        tmp = path.with_suffix(".opt.png")
        quantized.save(tmp, format="PNG", optimize=True, pnginfo=meta)
    after = tmp.stat().st_size
    if after >= before:
        tmp.unlink()
        return before, before
    tmp.replace(path)
    return before, after


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__)
        return 2
    root = Path(argv[1])
    if not root.is_dir():
        print(f"Klasor bulunamadi: {root}")
        return 1

    files = sorted(root.rglob("*.png"))
    if not files:
        print(f"PNG bulunamadi: {root}")
        return 1

    before = after = 0
    with ProcessPoolExecutor() as pool:
        for old, new in pool.map(optimize, files):
            before += old
            after += new

    ratio = 100 if before == 0 else round(100 * after / before)
    print(
        f"Logo optimizasyonu: {len(files)} dosya, "
        f"{before / 1024 / 1024:.2f} MB -> {after / 1024 / 1024:.2f} MB (%{ratio})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
