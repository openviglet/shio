#!/usr/bin/env python
"""Vendor the roadkeep engine into ``.roadkeep/``, the way ``npm install`` fills ``node_modules``.

Why the engine lives in the repository
--------------------------------------
roadkeep owns this project's ROADMAP / CHANGELOG / IMPROVEMENTS, so a session runs it constantly.
Running it from wherever it happens to be found costs two things every day:

* **Every distinct command path is a fresh authorization prompt.** A copy under a plugin cache or
  a scratch directory changes path per version and per machine, so the allowance never sticks.
  ``.roadkeep/scripts/roadkeep.py`` is one stable path, allow-listed once in
  ``.claude/settings.json``.
* **Which copy answers is not decidable by looking.** A machine can carry several plugin caches at
  different versions across two config directories, and a live checkout can be mid-refactor — a
  session lost time to ``ImportError: cannot import name '_print_claim'`` out of a half-edited tree.

``ROADKEEP_HOME`` points here (``.claude/settings.json``), which is the launcher's own first
resolution step, so the guard hook, the MCP server and a hand-run command all reach one engine.
``.roadkeep/`` is git-ignored: reproduced by this script, never committed.

Written in Python rather than Node deliberately: roadkeep *is* Python, so the interpreter this needs
is the one the thing being installed already requires. A Node implementation would add a dependency
that a .NET or Java repository has no other reason to carry.

The source is chosen by **version**, never by position — candidates are asked ``--version`` and the
highest wins. Set ``ROADKEEP_SRC`` to force one.

Usage::

    python scripts/install_roadkeep.py            vendor the newest engine found
    python scripts/install_roadkeep.py --check    report only; exit 1 when out of date
"""
from __future__ import annotations

import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / ".roadkeep"
ENGINE = Path("scripts") / "roadkeep.py"


def plugin_roots() -> list[Path]:
    """Every plugin or marketplace tree that could hold an engine, both config directories."""
    configs = {
        Path(p)
        for p in (
            os.environ.get("CLAUDE_CONFIG_DIR"),
            Path.home() / ".claude",
            Path.home() / ".claude-pessoal",
        )
        if p
    }
    found: list[Path] = []
    for config in configs:
        for sub in ("plugins/cache/alegauss/roadkeep", "plugins/marketplaces/alegauss"):
            base = config / sub
            if not base.is_dir():
                continue
            if (base / ENGINE).is_file():
                found.append(base)
            # The cache nests one directory per installed version.
            for entry in sorted(p for p in base.iterdir() if p.is_dir()):
                if (entry / ENGINE).is_file():
                    found.append(entry)
    return found


def version_of(root: Path) -> str | None:
    """What a candidate says it is, or None. A tree that cannot answer is not a source."""
    try:
        out = subprocess.run(
            [sys.executable, str(root / ENGINE), "--version"],
            capture_output=True, text=True, timeout=60, check=False,
        ).stdout
    except (OSError, subprocess.SubprocessError):
        return None
    found = re.search(r"roadkeep\s+(\d+(?:\.\d+)*)", out)
    return found.group(1) if found else None


def as_tuple(version: str) -> tuple[int, ...]:
    return tuple(int(part) for part in version.split("."))


def main() -> int:
    # ROADKEEP_SRC first, then the launcher's own sibling rule, then the plugin caches. A working
    # CHECKOUT is deliberately not searched for beyond that sibling: it is the one source that can
    # be mid-refactor. Point ROADKEEP_SRC at a checkout when that is what you want, so it is a
    # decision rather than a default.
    candidates = [
        Path(os.environ["ROADKEEP_SRC"]) if os.environ.get("ROADKEEP_SRC") else None,
        ROOT.parent / "roadkeep",
        *plugin_roots(),
    ]
    rated = []
    for root in candidates:
        if root is None or not (root / ENGINE).is_file():
            continue
        version = version_of(root)
        if version:
            rated.append((as_tuple(version), version, root))
    rated.sort(reverse=True)

    if not rated:
        print(
            "install-roadkeep: no roadkeep engine found.\n"
            "  Looked for scripts/roadkeep.py under $ROADKEEP_SRC, a sibling checkout, and the\n"
            "  alegauss plugin caches. Install the plugin, or set ROADKEEP_SRC.",
            file=sys.stderr,
        )
        return 1

    installed = version_of(TARGET) if (TARGET / ENGINE).is_file() else None
    _, best_version, best_root = rated[0]

    print(f"install-roadkeep: {len(rated)} candidate(s); newest is {best_version}")
    for _, version, root in rated:
        print(f"  {version:<10} {root}")
    print(f"  vendored   {installed or '(none)'} -> {TARGET}")

    if "--check" in sys.argv:
        return 0 if installed == best_version else 1

    if installed == best_version:
        print(f"install-roadkeep: .roadkeep is already {installed}, nothing to do.")
        return 0

    # `.git` is excluded deliberately: this is a vendored artefact, not a second checkout, and a
    # nested repository is what makes `git status` in the parent confusing.
    shutil.rmtree(TARGET, ignore_errors=True)
    shutil.copytree(best_root, TARGET, ignore=shutil.ignore_patterns(".git"))

    now = version_of(TARGET)
    if now != best_version:
        print(f"install-roadkeep: copied {best_version} but .roadkeep answers {now}", file=sys.stderr)
        return 1
    print(f"install-roadkeep: .roadkeep is now {now} (from {best_root})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
