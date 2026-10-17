#!/usr/bin/env python3
"""Render the start-android templates into a project (Python 3 standard library only).

    python3 -I scaffold.py --dest . --mode native --network retrofit --base-package com.acme.shop \
        --app-name AcmeShop --base-url-prod api.acme.com/v1/ --base-url-dev dev.api.acme.com/v1/ --ci

    python3 -I scaffold.py --dest . --mode kmp-sdk --base-package com.acme.shop --app-name AcmeShop \
        --sdk-coordinates com.acme:sdk:1.4.0 --sdk-repo https://maven.pkg.github.com/acme/acme-sdk

What it does:
  1. Renders templates/base plus the overlays for the chosen options into a staging folder:
     native -> templates/native + templates/network-<retrofit|ktor>; kmp-sdk -> templates/kmp-sdk;
     --ci -> templates/ci. Placeholders (__PKG__, __APP_ID__, ...) are replaced in contents and paths.
     Lines between `//@if <flag>` (or `#@if`, `<!--@if ...-->`) and the matching `@endif` are kept
     only when the flag is on. Flags: native, sdk, retrofit, ktor, ci.
  2. Copies every staged file that does not exist in --dest yet.
  3. Files named <name>.append are merged line by line into <name> (only missing lines/keys).
  4. Never overwrites: existing files are reported as conflicts (with the staged path) so the agent
     merges them by hand. --force overwrites instead, after writing a .bak copy.
  5. Writes .tezca-android.json (only if missing).

It prints a JSON report on stdout: {"staging", "created", "conflicts", "identical", "appended", "manual"}.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
TEMPLATES = HERE.parent / "assets" / "templates"
PLUGIN_VERSION = "0.1.0"
BINARY_SUFFIXES = {".png", ".jpg", ".jar", ".webp", ".ico"}
IF_RE = re.compile(r"^\s*(?://|#|<!--)\s*@if\s+(!?)(\w+)\s*(?:-->)?\s*$")
ENDIF_RE = re.compile(r"^\s*(?://|#|<!--)\s*@endif\s*(?:-->)?\s*$")


def pascal(name: str) -> str:
    return "".join(part[:1].upper() + part[1:] for part in re.split(r"[^A-Za-z0-9]+", name) if part)


def normalize_url(url: str) -> str:
    url = url.strip()
    if not re.match(r"^https?://", url):
        url = "https://" + url
    return url.rstrip("/") + "/"


def placeholders(args: argparse.Namespace) -> dict[str, str]:
    pkg = args.base_package
    app_id = args.app_id or pkg
    group = artifact = version = ""
    if args.mode == "kmp-sdk":
        parts = args.sdk_coordinates.split(":")
        if len(parts) != 3:
            raise SystemExit("--sdk-coordinates must look like group:artifact:version")
        group, artifact, version = parts
    prod = normalize_url(args.base_url_prod)
    dev = normalize_url(args.base_url_dev or args.base_url_prod)
    return {
        "__PKG_PATH__": pkg.replace(".", "/"),
        "__PKG__": pkg,
        "__APP_ID_PATH__": app_id.replace(".", "/"),
        "__APP_ID__": app_id,
        "__APP_NAME__": args.app_name,
        "__APP_CLASS__": pascal(args.app_name),
        "__PROJECT_NAME__": args.project_name or pascal(args.app_name),
        "__BASE_URL_PROD__": prod,
        "__BASE_URL_DEV__": dev,
        "__SDK_GROUP__": group,
        "__SDK_ARTIFACT__": artifact,
        "__SDK_VERSION__": version,
        "__SDK_REPO_URL__": args.sdk_repo or "",
        "__MIN_SDK__": str(args.min_sdk),
    }


def flags(args: argparse.Namespace) -> set[str]:
    result = {args.mode.replace("kmp-", "")}  # native | sdk
    if args.mode == "native":
        result.add(args.network)
    if args.ci:
        result.add("ci")
    return result


def substitute(text: str, values: dict[str, str]) -> str:
    # Longest keys first so __PKG_PATH__ wins over __PKG__.
    for key in sorted(values, key=len, reverse=True):
        text = text.replace(key, values[key])
    return text


def apply_conditionals(text: str, on: set[str], source: Path) -> str:
    out: list[str] = []
    stack: list[bool] = []
    for line in text.splitlines(keepends=True):
        if m := IF_RE.match(line):
            negate, flag = m.group(1) == "!", m.group(2)
            stack.append((flag in on) != negate)
            continue
        if ENDIF_RE.match(line):
            if not stack:
                raise SystemExit(f"Unbalanced @endif in {source}")
            stack.pop()
            continue
        if all(stack):
            out.append(line)
    if stack:
        raise SystemExit(f"Unclosed @if in {source}")
    return "".join(out)


def template_roots(args: argparse.Namespace) -> list[Path]:
    roots = [TEMPLATES / "base"]
    if args.mode == "native":
        roots += [TEMPLATES / "native", TEMPLATES / f"network-{args.network}"]
    else:
        roots.append(TEMPLATES / "kmp-sdk")
    if args.ci:
        roots.append(TEMPLATES / "ci")
    return roots


def render(args: argparse.Namespace, staging: Path) -> None:
    values = placeholders(args)
    on = flags(args)
    for root in template_roots(args):
        for src in sorted(p for p in root.rglob("*") if p.is_file()):
            if src.name == ".DS_Store":
                continue
            rel = src.relative_to(root)
            out = staging / substitute(str(rel), values)
            out.parent.mkdir(parents=True, exist_ok=True)
            if src.suffix in BINARY_SUFFIXES:
                shutil.copy2(src, out)
                continue
            text = apply_conditionals(src.read_text(encoding="utf-8"), on, src)
            out.write_text(substitute(text, values), encoding="utf-8")
            if src.stat().st_mode & 0o111:
                out.chmod(0o755)


def _same_text(a: Path, b: Path) -> bool:
    """Equal except for trailing whitespace / final newline."""
    try:
        norm = lambda p: "\n".join(line.rstrip() for line in p.read_text(encoding="utf-8").strip().splitlines())  # noqa: E731
        return norm(a) == norm(b)
    except UnicodeDecodeError:
        return False


def install(staging: Path, dest: Path, force: bool, dry_run: bool) -> dict[str, list[str]]:
    report: dict[str, list[str]] = {"created": [], "conflicts": [], "identical": [], "overwritten": [], "appended": [], "manual": []}
    for staged in sorted(p for p in staging.rglob("*") if p.is_file()):
        rel = staged.relative_to(staging)
        target = dest / rel
        if staged.suffix == ".append":
            target = target.with_suffix("")
            existing = target.read_text(encoding="utf-8").splitlines() if target.exists() else []
            keys = {line.split("=", 1)[0].strip() for line in existing if "=" in line and not line.startswith("#")}
            missing = [
                line for line in staged.read_text(encoding="utf-8").splitlines()
                if line.strip() and line not in existing and ("=" not in line or line.split("=", 1)[0].strip() not in keys)
            ]
            if missing and not dry_run:
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open("a", encoding="utf-8") as fh:
                    lead = "\n" if existing and existing[-1].strip() else ""
                    fh.write(lead + "\n".join(missing) + "\n")
            if missing:
                report["appended"].append(f"{target.relative_to(dest)}: {', '.join(missing)}")
            continue
        if target.exists():
            if target.read_bytes() == staged.read_bytes() or _same_text(target, staged):
                report["identical"].append(str(rel))
            elif force:
                if not dry_run:
                    shutil.copy2(target, target.with_name(target.name + ".bak"))
                    shutil.copy2(staged, target)
                report["overwritten"].append(str(rel))
            else:
                report["conflicts"].append(str(rel))
            continue
        if not dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(staged, target)
        report["created"].append(str(rel))
    return report


def write_config(args: argparse.Namespace, dest: Path, dry_run: bool, report: dict[str, list[str]]) -> None:
    path = dest / ".tezca-android.json"
    if path.exists():
        report["manual"].append(".tezca-android.json already exists: left untouched (update it by hand if the options changed)")
        return
    config: dict[str, object] = {
        "mode": args.mode,
        "network": args.network if args.mode == "native" else "none",
        "basePackage": args.base_package,
        "appId": args.app_id or args.base_package,
        "di": "hilt",
        "navigation": "navigation3",
        "features": [],
        "pluginVersion": PLUGIN_VERSION,
    }
    if args.mode == "kmp-sdk":
        config["sdk"] = {
            "coordinates": args.sdk_coordinates,
            "repo": args.sdk_repo,
            "exposes": args.sdk_exposes,
            "locator": args.sdk_locator,
        }
    if not dry_run:
        path.write_text(json.dumps(config, indent=2) + "\n", encoding="utf-8")
    report["created"].append(".tezca-android.json")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dest", required=True, type=Path)
    parser.add_argument("--mode", choices=["native", "kmp-sdk"], required=True)
    parser.add_argument("--network", choices=["retrofit", "ktor"], default="retrofit", help="native mode only")
    parser.add_argument("--base-package", required=True, help="e.g. com.acme.shop (also the core/feature package root)")
    parser.add_argument("--app-name", required=True, help="Display name, e.g. 'Acme Shop'")
    parser.add_argument("--project-name", help="rootProject.name (default: PascalCase app name)")
    parser.add_argument("--app-id", help="applicationId (default: --base-package)")
    parser.add_argument("--base-url-prod", default="api.example.com/")
    parser.add_argument("--base-url-dev")
    parser.add_argument("--min-sdk", type=int, default=26, help="minSdk (kmp-sdk mode: at least the SDK's own minSdk)")
    parser.add_argument("--sdk-coordinates", help="kmp-sdk mode: group:artifact:version")
    parser.add_argument("--sdk-repo", help="kmp-sdk mode: Maven repo URL (GitHub Packages)")
    parser.add_argument("--sdk-exposes", choices=["usecases", "viewmodels", "both"], default="usecases")
    parser.add_argument("--sdk-locator", default="", help="kmp-sdk mode: how to obtain an SDK object, e.g. 'SetupDi.getUseCase<T>()'")
    parser.add_argument("--ci", action="store_true", help="add GitHub Actions workflows")
    parser.add_argument("--staging", type=Path, help="Keep the rendered tree here (default: temp dir)")
    parser.add_argument("--force", action="store_true", help="Overwrite conflicting files (writes .bak)")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", args.base_package):
        parser.error("--base-package must look like com.company.app (lowercase, dot separated)")
    if args.app_id and not re.fullmatch(r"[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+", args.app_id):
        parser.error("--app-id must look like com.company.app")
    if args.mode == "kmp-sdk" and not (args.sdk_coordinates and args.sdk_repo):
        parser.error("kmp-sdk mode needs --sdk-coordinates and --sdk-repo")
    dest = args.dest.expanduser().resolve()
    if not dest.is_dir():
        parser.error(f"--dest {dest} is not a directory")

    staging = args.staging or Path(tempfile.mkdtemp(prefix="tezca-android-"))
    if staging.exists() and any(staging.iterdir()):
        shutil.rmtree(staging)
    staging.mkdir(parents=True, exist_ok=True)

    render(args, staging)
    report = install(staging, dest, args.force, args.dry_run)
    write_config(args, dest, args.dry_run, report)
    if not (dest / "gradlew").exists():
        report["manual"].append("No Gradle wrapper: run `gradle wrapper --gradle-version <see references/versions.md>` in the project root")
    if not (dest / "local.properties").exists():
        report["manual"].append("No local.properties: create it with sdk.dir=<Android SDK path> (or export ANDROID_HOME)")
    print(json.dumps({"staging": str(staging), "dry_run": args.dry_run, **report}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
