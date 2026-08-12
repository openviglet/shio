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
  2. a sibling checkout ``../roadkeep``              two repos cloned side by side
  3. a cached shallow clone in the user cache dir    the web, second turn onward
  4. ``git clone alegauss/roadkeep`` into that cache the web, first turn (needs net)

Two rules keep it from ever making things worse:

  * **Defer to the plugin.** Where the harness has the roadkeep plugin enabled *for this
    project*, its own hook and server already run, so both modes here become a silent
    no-op — nothing double-fires and there is never a second ``roadkeep`` server or a
    doubled deny message. That question is a row in the harness's registry and never a
    file on disk: see :func:`_plugin_is_wired`.
  * **Never block a turn.** If no engine can be found or cloned, every mode exits 0 and
    emits nothing. A missing roadkeep must degrade to "unenforced", never to a broken
    session or a failed hook.

The engine invoked is ``scripts/roadkeep.py`` — roadkeep's own launcher, which puts its
``src`` on ``sys.path`` and calls ``roadkeep.cli.main``. So the arguments, exit codes and
refusals are the console script's own; this file only decides *which copy answers*.
"""

from __future__ import annotations

import json
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


def _config_home() -> Path:
    """The harness's config directory — ``$CLAUDE_CONFIG_DIR`` or ``~/.claude``.

    The same pair Claude Code itself resolves, and the reason this is a function: a
    hardcoded ``~/.claude`` reads a directory the running harness may not be using, so on a
    machine with two config dirs it answers about the wrong install. Measured: two
    independent roadkeep installs, 0.1.721 under ``~/.claude`` and 0.1.678 under
    ``~/.claude-pessoal``, both wired to this project.
    """
    return Path(os.environ.get("CLAUDE_CONFIG_DIR") or Path.home() / ".claude")


def _plugin_is_wired() -> bool:
    """Whether the harness has the roadkeep plugin enabled **for this project**.

    The signal to stand down, and it is deliberately not "is there a copy on disk". A
    marketplace clone and every cached version live under ``plugins/`` whether or not this
    project uses them, so a glob for ``scripts/roadkeep.py`` finds a file in cases where no
    hook is loaded — and then this launcher defers to a plugin that never ran and nothing
    guards the write. What decides it is whether the *hooks this file would duplicate* are
    live, which is a row in the harness's own registry, matched on the project path.

    Read defensively and never written: the file is the harness's. A registry this cannot
    parse answers False, which is the safe side of the two — a doubled deny message is a
    cosmetic fault and an absent guard is the drift roadkeep exists to stop.
    """
    registry = _config_home() / "plugins" / "installed_plugins.json"
    try:
        payload = json.loads(registry.read_text(encoding="utf-8"))
        wanted = _repo_root().resolve()
    except (OSError, ValueError):
        return False
    plugins = payload.get("plugins") if isinstance(payload, dict) else None
    for key, rows in (plugins if isinstance(plugins, dict) else {}).items():
        # `<name>@<marketplace>`; the marketplace is not ours to care about, the name is.
        if not isinstance(key, str) or key.partition("@")[0] != "roadkeep":
            continue
        for row in rows if isinstance(rows, list) else ():
            stated = row.get("projectPath") if isinstance(row, dict) else None
            if not isinstance(stated, str) or not stated:
                continue
            try:
                # Resolved and never compared as text: the harness writes the path the way
                # its platform spells it, and this repo is reached through a junction, so
                # `shio\latest` and `shio\2026.3` are one project written twice.
                if Path(stated).resolve() == wanted:
                    return True
            except OSError:
                continue
    return False


def _guard(argv: list[str]) -> int:
    """Run roadkeep's ``guard`` on the hook payload, or step aside.

    The payload on stdin is read here and handed on, because a stream is readable once.
    """
    payload = sys.stdin.buffer.read()
    if _plugin_is_wired():
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
    if _plugin_is_wired():
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
