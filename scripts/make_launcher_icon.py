# -*- coding: utf-8 -*-
"""
从设计稿生成 Android 自适应图标的前景位图（5 个密度）。

设计稿是 RGB 白底 + 浅灰棋盘格的图（不是真透明），所以先按「与边界连通的浅色」
判定背景并抠掉，卡片内部的高光因为不与边界连通会被保留。外发光是蓝色，不会被误删。

用法：换设计稿后改 SRC 再跑一次
    python scripts/make_launcher_icon.py

输出：drawable-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher_foreground.png
主体（含光晕）占画布 66%，落在自适应图标安全区（66/108 ≈ 61%）内。
"""
import os

import numpy as np
from PIL import Image
from scipy import ndimage

SRC = r"D:\Chrome\download\信用卡记账app图标设计需求.png"
RES = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "android-app", "app", "src", "main", "res",
)

# 卡片是横置矩形，要让四角都落在圆形遮罩（直径 72dp）里：
# W² + H² ≤ 72²，H ≈ 0.72W → W ≤ 58dp；换算成含光晕的主体宽 ≈ 0.57 画布
CONTENT_RATIO = 0.57
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}

# 判定「浅色」的阈值：棋盘格最暗约 248，卡片内部白色高光可达 255
LIGHT_MIN = 235
FEATHER_SIGMA = 1.2


def cut_background(path: str) -> Image.Image:
    rgb = Image.open(path).convert("RGB")
    arr = np.asarray(rgb).astype(np.int16)

    light = arr.min(axis=2) > LIGHT_MIN

    # 只把「和图片四边连通」的浅色算作背景，避免抠穿卡片内部的高光
    labels, _ = ndimage.label(light)
    border = set(labels[0, :]) | set(labels[-1, :]) | set(labels[:, 0]) | set(labels[:, -1])
    border.discard(0)
    background = np.isin(labels, list(border)) if border else np.zeros_like(light)

    alpha = np.where(background, 0.0, 255.0).astype(np.float32)
    alpha = ndimage.gaussian_filter(alpha, sigma=FEATHER_SIGMA)

    rgba = np.dstack([np.asarray(rgb), np.clip(alpha, 0, 255).astype(np.uint8)])
    return Image.fromarray(rgba, "RGBA")


def main() -> None:
    cut = cut_background(SRC)
    bbox = cut.getchannel("A").getbbox()
    if bbox is None:
        raise SystemExit("抠图后没有剩余内容，检查 LIGHT_MIN 阈值")
    content = cut.crop(bbox)
    print("原图 2048x2048 -> 抠图后主体 {}".format(content.size))

    for density_name, density in DENSITIES.items():
        canvas = int(round(108 * density))
        ratio = (canvas * CONTENT_RATIO) / content.width
        w = max(1, int(round(content.width * ratio)))
        h = max(1, int(round(content.height * ratio)))
        if h > canvas * 0.94:
            ratio = (canvas * 0.94) / content.height
            w = max(1, int(round(content.width * ratio)))
            h = max(1, int(round(content.height * ratio)))

        resized = content.resize((w, h), Image.LANCZOS)
        out = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        out.alpha_composite(resized, ((canvas - w) // 2, (canvas - h) // 2))

        target_dir = os.path.join(RES, "drawable-" + density_name)
        os.makedirs(target_dir, exist_ok=True)
        target = os.path.join(target_dir, "ic_launcher_foreground.png")
        out.save(target, "PNG", optimize=True)
        print("  {:<8} {:>3}px -> {}".format(density_name, canvas, target))


if __name__ == "__main__":
    main()
