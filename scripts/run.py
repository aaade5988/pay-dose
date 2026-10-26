# -*- coding: utf-8 -*-
"""一键启动 pay-dose 后端。

用法：
    python scripts/run.py                 # 构建 + 启动（默认 8000 端口）
    python scripts/run.py --port 8001     # 换端口
    python scripts/run.py --skip-build    # 跳过构建，直接跑已有 jar
    python scripts/run.py --db D:/mydata/pay.db   # 指定 SQLite 文件位置
"""

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "backend"
JAR = ROOT / "target" / "pay-dose.jar"

# 本机已安装的 JDK 候选（Spring Boot 3 需要 17+）
JDK_CANDIDATES = [
    r"D:\Program Files\Java\jdk-17",
    r"C:\Program Files\Java\jdk-17",
    r"D:\Program Files\Java\jdk-21",
]

MAVEN_CANDIDATES = [
    r"D:\Maven\apache-maven-3.6.3\bin\mvn.cmd",
    "mvn",
]


def find_java_home() -> str:
    env = os.environ.get("JAVA_HOME")
    if env and (Path(env) / "bin" / "java.exe").exists():
        return env
    for cand in JDK_CANDIDATES:
        if (Path(cand) / "bin" / "java.exe").exists():
            return cand
    sys.exit("未找到 JDK 17+，请设置 JAVA_HOME 环境变量")


def find_maven() -> str:
    for cand in MAVEN_CANDIDATES:
        if cand == "mvn":
            found = shutil.which("mvn")
            if found:
                return found
        elif Path(cand).exists():
            return cand
    sys.exit("未找到 Maven，请把 mvn 加入 PATH")


def main() -> None:
    parser = argparse.ArgumentParser(description="启动 pay-dose 后端")
    parser.add_argument("--port", default="8000", help="服务端口（默认 8000）")
    parser.add_argument("--db", default=None, help="SQLite 文件路径（默认 backend/data/pay-dose.db）")
    parser.add_argument("--skip-build", action="store_true", help="跳过构建")
    args = parser.parse_args()

    java_home = find_java_home()
    # 关键：把 JDK 17 提到 PATH 最前，否则 mvn 会用默认的 JDK 1.8
    os.environ["JAVA_HOME"] = java_home
    os.environ["PATH"] = str(Path(java_home) / "bin") + os.pathsep + os.environ.get("PATH", "")

    if not args.skip_build:
        mvn = find_maven()
        print(f"[1/2] 构建中（JAVA_HOME={java_home}）...")
        result = subprocess.run([mvn, "-B", "-q", "-DskipTests", "package"], cwd=ROOT)
        if result.returncode != 0:
            sys.exit("构建失败，请检查上方错误输出")
    else:
        print("[1/2] 跳过构建")

    if not JAR.exists():
        sys.exit(f"未找到 {JAR}，请先不带 --skip-build 运行一次")

    env = dict(os.environ)
    env["SERVER_PORT"] = args.port
    if args.db:
        env["PAY_DOSE_DB"] = str(Path(args.db).resolve())

    db_display = env.get("PAY_DOSE_DB", str(ROOT / "data" / "pay-dose.db"))
    print(f"[2/2] 启动服务 http://127.0.0.1:{args.port}")
    print(f"      数据库 {db_display}")
    print("      Ctrl+C 停止")
    subprocess.run([str(Path(java_home) / "bin" / "java.exe"), "-jar", str(JAR)], env=env, cwd=ROOT)


if __name__ == "__main__":
    main()
