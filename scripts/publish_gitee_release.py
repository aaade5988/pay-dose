#!/usr/bin/env python3
"""把编译好的 APK 发到 Gitee 发行版（Release）。

一条命令搞定：打 release 包 → 复制到 dist/ → 创建/复用 Release → 上传 APK 附件 → 回读校验。

token 获取顺序：
  1. --token 参数
  2. 环境变量 GITEE_TOKEN
  3. 文件 %USERPROFILE%\\.gitee_token（取第一行；避免 token 出现在命令行历史里）

用法：
    python scripts/publish_gitee_release.py                  # 打包并上传
    python scripts/publish_gitee_release.py --skip-build     # 只上传 dist 里现成的包
    python scripts/publish_gitee_release.py --tag v1.0.4     # 与 versionName 不同的 tag

前置条件：
  - 仓库必须**公开**：App 匿名查 /releases/latest，私有仓库只会拿到 404；
  - token 需要 projects 权限（Gitee → 设置 → 私人令牌）。
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GRADLE_FILE = ROOT / "android-app" / "app" / "build.gradle.kts"
APK_SRC = ROOT / "android-app" / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
API = "https://gitee.com/api/v5"
DEFAULT_REPO = "mechanical-running-roll-wang/pay-dose"


def read_version_name() -> str:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    m = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not m:
        raise SystemExit("在 build.gradle.kts 里找不到 versionName")
    return m.group(1)


def read_version_code() -> str:
    text = GRADLE_FILE.read_text(encoding="utf-8")
    m = re.search(r"versionCode\s*=\s*(\d+)", text)
    return m.group(1) if m else "?"


def resolve_token(explicit: str | None) -> str:
    if explicit:
        return explicit.strip()
    env = os.environ.get("GITEE_TOKEN")
    if env:
        return env.strip()
    token_file = Path(os.path.expanduser("~")) / ".gitee_token"
    if token_file.exists():
        return token_file.read_text(encoding="utf-8").strip().splitlines()[0].strip()
    raise SystemExit(
        "缺少 token。三选一：\n"
        "  1) python scripts/publish_gitee_release.py --token <token>\n"
        "  2) set GITEE_TOKEN=<token>\n"
        "  3) 把 token 写进 %USERPROFILE%\\.gitee_token（推荐，不进命令行历史）\n"
        "token 生成：Gitee → 设置 → 私人令牌，勾选 projects"
    )


def request_json(method: str, path: str, body: dict | None = None) -> tuple[int, dict | list | str]:
    url = API + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("User-Agent", "pay-dose-release")
    if data:
        req.add_header("Content-Type", "application/json;charset=UTF-8")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "null")
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw[:300]
    except Exception as e:  # 网络问题
        return -1, repr(e)


def upload_file(path: str, release_id: str, token: str, apk: Path) -> tuple[int, dict | str]:
    """手写 multipart：Gitee 的 attach_files 只收 form-data。"""
    boundary = "----paydose" + uuid.uuid4().hex
    parts: list[bytes] = []

    def field(name: str, value: str) -> None:
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
        )

    field("access_token", token)
    mime = mimetypes.guess_type(apk.name)[0] or "application/vnd.android.package-archive"
    parts.append(
        (
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{apk.name}"\r\n'
            f"Content-Type: {mime}\r\n\r\n"
        ).encode()
    )
    parts.append(apk.read_bytes())
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    body = b"".join(parts)

    req = urllib.request.Request(API + path, data=body, method="POST")
    req.add_header("User-Agent", "pay-dose-release")
    req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8") or "null")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")[:300]
    except Exception as e:
        return -1, repr(e)


def build_apk() -> None:
    gradlew = ROOT / "android-app" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    print("[1/4] 打包 release …")
    proc = subprocess.run(
        [str(gradlew), ":app:assembleRelease", "--offline"],
        cwd=ROOT / "android-app",
    )
    if proc.returncode != 0:
        raise SystemExit(f"构建失败（退出码 {proc.returncode}）")
    if not APK_SRC.exists():
        raise SystemExit(f"找不到构建产物：{APK_SRC}")


def main() -> int:
    parser = argparse.ArgumentParser(description="发布 APK 到 Gitee Release")
    parser.add_argument("--repo", default=DEFAULT_REPO, help="owner/repo")
    parser.add_argument("--tag", default=None, help="默认 v<versionName>")
    parser.add_argument("--token", default=None)
    parser.add_argument("--apk", default=None, help="默认 dist/pay-dose-<version>.apk")
    parser.add_argument("--notes", default=None, help="更新说明；默认读 dist/release-notes-<version>.md")
    parser.add_argument("--skip-build", action="store_true", help="跳过 gradle，直接上传现成 APK")
    parser.add_argument("--prerelease", action="store_true")
    args = parser.parse_args()

    version = read_version_name()
    code = read_version_code()
    tag = args.tag or f"v{version}"
    token = resolve_token(args.token)

    dist_apk = Path(args.apk) if args.apk else ROOT / "dist" / f"pay-dose-{version}.apk"

    if not args.skip_build:
        build_apk()
        dist_apk.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(APK_SRC, dist_apk)
        print(f"    APK → {dist_apk}")

    if not dist_apk.exists():
        raise SystemExit(f"APK 不存在：{dist_apk}（去掉 --skip-build 让它先构建）")

    notes = args.notes
    if not notes:
        note_file = ROOT / "dist" / f"release-notes-{version}.md"
        notes = note_file.read_text(encoding="utf-8") if note_file.exists() else f"版本 {version}"

    size_mb = dist_apk.stat().st_size / 1024 / 1024
    print(f"[2/4] 目标：{args.repo}  tag={tag}  APK={dist_apk.name}（{size_mb:.2f} MB）")

    # 已有同 tag 的 Release 就复用，否则新建（Gitee 会顺带建 tag）
    # 注意：Gitee 查不到时返回的是 200 + null，而不是 404，所以两种都要当「不存在」
    status, data = request_json(
        "GET", f"/repos/{args.repo}/releases/tags/{urllib.parse.quote(tag)}?access_token={token}"
    )
    existing_id = data.get("id") if isinstance(data, dict) else None
    if existing_id:
        release_id = existing_id
        print(f"[3/4] 复用已有 Release #{release_id}")
    elif status in (200, 404):
        status, data = request_json(
            "POST",
            f"/repos/{args.repo}/releases",
            {
                "access_token": token,
                "tag_name": tag,
                "name": tag,
                "body": notes,
                "target_commitish": "master",
                "prerelease": bool(args.prerelease),
            },
        )
        if status not in (200, 201) or not isinstance(data, dict):
            raise SystemExit(f"创建 Release 失败：HTTP {status} {data}")
        release_id = data["id"]
        print(f"[3/4] 新建 Release #{release_id}")
    else:
        raise SystemExit(f"查询 Release 失败：HTTP {status} {data}")

    print("[4/4] 上传附件 …")
    status, data = upload_file(
        f"/repos/{args.repo}/releases/{release_id}/attach_files", release_id, token, dist_apk
    )
    if status not in (200, 201):
        raise SystemExit(f"上传失败：HTTP {status} {data}")

    # 回读校验：确认附件真的挂上去了
    status, data = request_json(
        "GET",
        f"/repos/{args.repo}/releases/tags/{urllib.parse.quote(tag)}?access_token={token}",
    )
    assets = data.get("assets", []) if isinstance(data, dict) else []
    print("\n完成：")
    for a in assets:
        print(f"  - {a.get('name')}  {int(a.get('size', 0)) / 1024 / 1024:.2f} MB")
        print(f"    {a.get('browser_download_url')}")
    if not assets:
        print("  （附件列表为空，请到网页确认）")
    print(f"\nRelease 页：https://gitee.com/{args.repo}/releases/tag/{tag}")
    print(f"提示：手机上的 versionName 必须低于 {version.lstrip('v')} 才会收到更新提示；当前构建 versionCode={code}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
