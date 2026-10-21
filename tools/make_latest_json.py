#!/usr/bin/env python3
"""
Делает latest.json для проверки обновлений в приложении.

Пример:
  python tools/make_latest_json.py app-release.apk \
      --version-code 200 --version-name 0.2.0 \
      --base-url https://github.com/ИМЯ/agenda-site/releases/download/v0.2.0/ \
      --notes "Аналитика и даты оценок" > latest.json

versionCode и versionName должны совпадать с теми, что собраны в APK (см. build.gradle.kts).
"""
import argparse
import hashlib
import json
import os
import sys


def sha256_of(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def build_manifest(apk_path, version_code, version_name, base_url, notes):
    if not os.path.isfile(apk_path):
        raise SystemExit(f"Файл не найден: {apk_path}")
    if not base_url.startswith("https://") or not base_url.endswith("/"):
        raise SystemExit("--base-url должен начинаться с https:// и заканчиваться на /")
    if version_code <= 0:
        raise SystemExit("--version-code должен быть положительным числом")
    return {
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl": base_url + os.path.basename(apk_path),
        "sha256": sha256_of(apk_path),
        "sizeBytes": os.path.getsize(apk_path),
        "notes": notes,
    }


def main():
    p = argparse.ArgumentParser(description="Генератор latest.json")
    p.add_argument("apk")
    p.add_argument("--version-code", type=int, required=True)
    p.add_argument("--version-name", required=True)
    p.add_argument("--base-url", required=True)
    p.add_argument("--notes", default="")
    a = p.parse_args()
    data = build_manifest(a.apk, a.version_code, a.version_name, a.base_url, a.notes)
    json.dump(data, sys.stdout, ensure_ascii=False, indent=2)
    print()


if __name__ == "__main__":
    main()
