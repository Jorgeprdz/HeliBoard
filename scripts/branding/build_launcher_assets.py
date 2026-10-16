#!/usr/bin/env python3
"""Generate Android launcher bitmaps and Play Store artwork from the approved icon."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "art/launcher_icon/approved_source_512.webp"
RES = ROOT / "app/src/main/res"

def main():
    approved = Image.open(SOURCE).convert("RGB")
    if approved.size != (512, 512):
        raise ValueError(f"Unexpected icon source size: {approved.size}")
    for density, size in [("mdpi",48),("hdpi",72),("xhdpi",96),("xxhdpi",144),("xxxhdpi",192)]:
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True,exist_ok=True)
        bitmap = approved.resize((size,size),Image.Resampling.LANCZOS)
        bitmap.save(folder/"ic_launcher.webp",format="WEBP",quality=90,method=6)
        # Pre-26 round icon: explicitly transparent outside the circular artwork.
        mask = Image.new("L",(size*4,size*4),0)
        ImageDraw.Draw(mask).ellipse((0,0,size*4-1,size*4-1),fill=255)
        mask = mask.resize((size,size),Image.Resampling.LANCZOS)
        round_icon = Image.new("RGBA",(size,size),(0,0,0,0))
        round_icon.paste(bitmap.convert("RGBA"),(0,0),mask)
        round_icon.save(folder/"ic_launcher_round.webp",format="WEBP",quality=90,method=6)
        # AAPT forbids duplicate resource names with different extensions.
        for legacy in ("ic_launcher.png","ic_launcher_round.png"):
            (folder/legacy).unlink(missing_ok=True)
    (ROOT/"art/launcher_icon/play-store-512.png").parent.mkdir(parents=True,exist_ok=True)
    approved.save(ROOT/"art/launcher_icon/play-store-512.png",format="PNG",optimize=True)
    print("Generated 10 density bitmaps and Google Play 512 PNG from approved artwork.")

if __name__ == "__main__":
    main()
