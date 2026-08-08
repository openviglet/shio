#!/usr/bin/env python
"""Self-locating roadkeep launcher for environments the plugin never reaches.

Why this file exists
--------------------
roadkeep ships as a Claude Code *plugin*. On a developer's machine `claude plugin
install` places it under ``~/.claude/plugins`` and its ``hooks/hooks.json``
registers the guard that **denies a hand-edit** of ``docs/ROADMAP.md``,
``docs/CHANGELOG.md`` and ``docs/IMPROVEMENTS.md`` and names the command to call
instead. But **Claude Code on the web has no ``/plugin`` command and never installs
marketplace plugins** — it only reads settings and files committed to the repository.
So in a cloud session the plugin's hooks and MCP server never load, the guard is
absent, and an agent silently falls back to editing the roadkeep-owned files by hand
("No roadkeep plugin/hooks -> I'll track via git log"). That is the exact drift
roadkeep exists to prevent.

This launcher is committed to the repo (which the web environment *does* read) and is
wired into ``.claude/settings.json`` (the ``guard`` hook) and ``.mcp.json`` (the
``mcp`` server). It finds a roadkeep engine at runtime and runs the same entry points
the plugin would, so the write path is enforced in every environment. The engine is
resolved in this order:

  1. ``$ROADKEEP_HOME/scripts/roadkeep.py``          explicit override
  2. an installed plugin under ``~/.claude/plugins`` the local developer machine
  3. a sibling checkout ``../roadkeep``              two repos cloned side by side
  4. a cached shallow clone in the user cache dir    the web, second turn onward
  5. ``git clone alegauss/roadkeep`` into that cache the web, first turn (needs net)

Two rules keep it from ever making things worse:

  * **Defer to the plugin.** If a plugin engine is present, the plugin's own hook and
    server already run, so both modes here become a silent no-op — nothing double-fires
    and there is never a second ``roadkeep`` server or a doubled deny message.
  * **Never block a turn.** If no engine can be found or cloned, every mode exits 0 and
    emits nothing. A missing roadkeep must degrade to "unenforced", never to a broken
    session or a failed hook.

The engine invoked is ``scripts/roadkeep.py`` — roadkeep's own launcher, which puts its
``src`` on ``sys.path`` and calls ``roadkeep.cli.main``. So the arguments, exit codes and
refusals are the console script's own; this file only decides *which copy answers*.
"""

from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path

REPO = "https://github.com/alegauss/roadkeep"
REF = "main"
ENGINE_REL = Path("scripts") / "roadkeep.py"


def _valid(root: Path | None) -> Path | None:
    """The engine path under *root*, if the file is actually there."""
    if root is None:
        return None
    engine = root / ENGINE_REL
    return engine if engine.is_file() else None


def _home_engine() -> Path | None:
    home = os.environ.get("ROADKEEP_HOME")
    return _valid(Path(home)) if home else None


def _plugin_engine() -> Path | None:
    """A roadkeep engine inside an installed Claude Code plugin, if one exists.

    Its presence is the signal to stand down: the plugin's own hooks and server are
    already wired, so this launcher must not add a second copy of either.
    """
    plugins = Path.home() / ".claude" / "plugins"
    if not plugins.is_dir():
        return None
    for engine in plugins.glob("**/" + ENGINE_REL.as_posix()):
        # Confirm it is roadkeep's, not some other plugin that happens to ship the path.
        if (engine.parent.parent / ".claude-plugin" / "plugin.json").is_file():
            return engine
    return None


def _repo_root() -> Path:
    """This checkout's root — ``.claude/hooks/roadkeep-launch.py`` -> up three."""
    env = os.environ.get("CLAUDE_PROJECT_DIR")
    if env:
        return Path(env)
    return Path(__file__).resolve().parents[2]


def _sibling_engine() -> Path | None:
    return _valid(_repo_root().parent / "roadkeep")


def _cache_root() -> Path:
    base = os.environ.get("XDG_CACHE_HOME") or (Path.home() / ".cache")
    return Path(base) / "roadkeep-src" / "roadkeep"


def _cache_engine() -> Path | None:
    return _valid(_cache_root())


def _clone() -> Path | None:
    """Shallow-clone roadkeep into the cache, once, for a web session that has neither
    the plugin nor a sibling checkout. Silent and best-effort: no network, no roadkeep,
    no error — the caller treats a ``None`` here exactly like "nothing found"."""
    if os.environ.get("ROADKEEP_NO_CLONE"):
        return None
    dest = _cache_root()
    try:
        dest.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", REF, REPO, str(dest)],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=60,
        )
    except Exception:
        return None
    return _cache_engine()


def _resolve() -> Path | None:
    """Find an engine to run (not the deferral check — that is the plugin's own job)."""
    return (
        _home_engine()
        or _sibling_engine()
        or _cache_engine()
        or _clone()
    )


def _guard(argv: list[str]) -> int:
    """Run roadkeep's ``guard`` on the hook payload, or step aside.

    The payload on stdin is read here and handed on, because a stream is readable once.
    """
    payload = sys.stdin.buffer.read()
    if _plugin_engine() is not None:
        return 0  # the plugin's hook already runs; do not double-fire.
    engine = _resolve()
    if engine is None:
        return 0  # unenforced beats broken.
    result = subprocess.run(
        [sys.executable, str(engine), "guard", *argv],
        input=payload,
    )
    return result.returncode


def _mcp(argv: list[str]) -> int:
    """Hand stdio to roadkeep's ``mcp`` server, or exit cleanly.

    Defers to the plugin the same way ``guard`` does, so a machine with the plugin runs
    exactly one roadkeep server. Uses ``execv`` so the server owns this process's stdio.
    """
    if _plugin_engine() is not None:
        return 0
    engine = _resolve()
    if engine is None:
        return 0
    os.execv(sys.executable, [sys.executable, str(engine), "mcp", *argv])
    return 0  # unreachable; execv replaces the process.


def main(argv: list[str]) -> int:
    if argv[:1] == ["guard"]:
        return _guard(argv[1:])
    if argv[:1] == ["mcp"]:
        return _mcp(argv[1:])
    sys.stderr.write("usage: roadkeep-launch.py {guard|mcp}\n")
    return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
