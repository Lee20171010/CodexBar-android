#!/usr/bin/env python3
"""Generate the notification small-icon silhouettes from the app launcher glyph.

Source: app/src/main/res/drawable-nodpi/ic_codexbar_original.png (1024x1024
RGBA: rounded dark badge #040404 with a light #F4F2F1/#8AC6B8 glyph). Android
notification small icons must be alpha-only white silhouettes, so the glyph is
extracted deterministically (no hand tracing):

  * source pixels with alpha < 128 are transparent
  * output alpha = clamp((luminance - 96) / (160 - 96), 0, 1) * 255
    (Rec. 601 luminance: dark badge -> 0, white/teal glyph -> 255,
    anti-aliased edges preserved)
  * crop to the glyph bounding box, LANCZOS-downscale so the glyph fills the
    Material 20/24 padding box, centered on a white RGBA canvas

Density mapping for a 24dp icon: mdpi 24, hdpi 36, xhdpi 48, xxhdpi 72,
xxxhdpi 96.

Usage (from the repository root): python3 tools/make_notification_icon.py
"""

import sys
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parent.parent
SOURCE = REPO / "app/src/main/res/drawable-nodpi/ic_codexbar_original.png"
DENSITIES = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}
ALPHA_MIN = 128
LUM_LOW, LUM_HIGH = 96.0, 160.0
PAD = 20 / 24  # glyph occupies 20 of 24 dp, Material icon convention


def extract_glyph(source: Image.Image) -> Image.Image:
    """Return the glyph as an L-mode alpha mask (deterministic luminance ramp)."""
    rgba = source.convert("RGBA")
    out = Image.new("L", rgba.size, 0)
    op, ip = out.load(), rgba.load()
    for y in range(rgba.size[1]):
        for x in range(rgba.size[0]):
            r, g, b, a = ip[x, y]
            if a < ALPHA_MIN:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            t = max(0.0, min(1.0, (lum - LUM_LOW) / (LUM_HIGH - LUM_LOW)))
            op[x, y] = round(t * 255)
    bbox = out.getbbox()
    assert bbox is not None, "no glyph pixels extracted; check thresholds"
    return out.crop(bbox)


def render(mask: Image.Image, px: int) -> Image.Image:
    """Center the glyph on a px x px white RGBA canvas at 20/24 padding."""
    inner = round(px * PAD)
    glyph = mask.resize(
        (inner, max(1, round(inner * mask.size[1] / mask.size[0]))),
        Image.LANCZOS,
    )
    canvas = Image.new("L", (px, px), 0)
    canvas.paste(glyph, ((px - inner) // 2, (px - glyph.size[1]) // 2))
    white = Image.new("RGBA", canvas.size, (255, 255, 255, 0))
    white.putalpha(canvas)
    return white


def main() -> int:
    assert SOURCE.is_file(), f"missing source asset: {SOURCE}"
    mask = extract_glyph(Image.open(SOURCE))
    for density, px in DENSITIES.items():
        out_dir = REPO / f"app/src/main/res/drawable-{density}"
        out_dir.mkdir(parents=True, exist_ok=True)
        icon = render(mask, px)
        assert icon.size == (px, px)
        hist = icon.getchannel("A").histogram()
        assert sum(hist[1:]) > 0, f"empty alpha for {density}"
        assert hist[0] > 0, f"no transparent pixels for {density}"
        rgb = icon.convert("RGB")
        assert rgb.getcolors(maxcolors=1) == [(px * px, (255, 255, 255))], (
            f"non-white RGB in {density}"
        )
        path = out_dir / "ic_notification.png"
        icon.save(path)
        print(f"wrote {path.relative_to(REPO)} ({px}x{px})")
    service = (
        REPO
        / "app/src/main/java/com/codexbar/android/core/notification/QuotaNotificationService.kt"
    ).read_text()
    assert service.count("setSmallIcon(R.drawable.ic_notification)") == 3, (
        "expected three ic_notification references in QuotaNotificationService"
    )
    assert "ic_quota" not in service, "stale ic_quota reference in QuotaNotificationService"
    return 0


if __name__ == "__main__":
    sys.exit(main())
