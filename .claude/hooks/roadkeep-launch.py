#!/usr/bin/env python
"""The launcher for an environment the plugin never reaches (RK1108).

Why this file exists
--------------------
roadkeep ships as a Claude Code *plugin*, and on a developer machine that is the whole
install: `/plugin install` places it under the harness's config directory and its
``hooks/hooks.json`` registers the guard that denies a hand-edit of the governed files.
There is one environment where none of that happens. **Claude Code on the web has no
``/plugin`` command and installs no marketplace plugin** — it reads settings and files
committed to the repository. So the hooks and the MCP server never load, the guard is
absent, and an agent falls back to editing ROADMAP.md by hand: the drift this tool exists
to stop, in the environment with the least supervision.

This file is committed to the adopting repository, which that environment *does* read.
``roadkeep install --committed`` writes it to ``.claude/hooks/`` and points the hook and
the server at it, so the write path is enforced wherever a session runs.

It is deliberately standalone — it runs *before* an engine has been found, so it may not
import :mod:`roadkeep`. That makes it the one file here that restates a rule stated in the
package, and `tests/test_launching.py` holds the two together: :func:`_config_home` is
:func:`roadkeep.provenance.installed`'s first two lines, and a closure test fails when
either moves. A second implementation nobody checks is how the version this replaced came
to look under ``~/.claude`` alone (see below).

The engine is resolved in this order:

  1. ``$ROADKEEP_HOME/scripts/roadkeep.py``          an explicit override
  2. a sibling checkout ``../roadkeep``              two repositories cloned side by side
  3. a cached clone under the user cache directory   the web, second turn onward

Three rules keep it from ever making things worse, and the first is the **guard's alone**:

  * **Defer to the plugin.** Where the harness has roadkeep enabled *for this project*, its
    own hook already runs, so ``guard`` here becomes a silent no-op and no deny message is
    doubled. That question is a row in the harness's registry and **never a file on disk**: this is
    the defect the shipped version exists to fix. A hand-written copy globbed
    ``~/.claude/plugins`` for ``scripts/roadkeep.py``, which finds a marketplace clone and
    every cached version whether or not the project uses any of them — so under a
    ``CLAUDE_CONFIG_DIR`` pointing elsewhere it stood down in favour of a plugin that was
    never loaded, and a hand edit of two governed files passed a session with a guard.
  * **Never block a turn.** If no engine is found, every mode exits 0 and emits nothing. A
    missing roadkeep must degrade to "unenforced", never to a broken session.
  * **Never reach the network.** The cache is used where something else populated it; this
    file does not clone. A hook that fetches code is a hook that runs code the repository
    did not commit, and the environment this exists for is the one that reviews it least.

``mcp`` keeps **neither of the first two**, and standing down there was the defect this was
reported from (RK1189). A stdio server has no way to say *another copy answers*: it either
speaks the protocol on the stdin the harness handed it or it exits, and an exit **is** how a
server reports a crash — so the deferral rendered the project's own declared server ``✗
failed`` in the harness's listing, in exactly the projects where everything was installed
correctly. Measured in Shio, where that is worse than cosmetic: the plugin's server and the
project's are both named ``roadkeep``, so the failed entry took the name and the session had
no roadkeep tools at all. ``install`` writes both declarations deliberately — the harness
reads them separately and only the hooks would fire twice (`installing.plan`) — so the file
it writes has to **serve wherever it is started**, and a missing engine is a refusal on
stderr for the reason a forwarded verb's is.

The engine invoked is ``scripts/roadkeep.py`` — roadkeep's own launcher, which puts its
``src`` on ``sys.path`` and calls ``roadkeep.cli.main``. So the arguments, exit codes and
refusals are that engine's own; this file only decides *which copy answers*.

Anything that is **not** one of those two modes is forwarded to that engine (RK1116). Both
modes are internal — the harness calls ``guard`` from a hook and ``mcp`` from ``.mcp.json`` —
so a file that answered only them resolved an engine for the two callers that are not the
agent and refused the one that was handed its name: the installed skill states ``python
".claude/hooks/roadkeep-launch.py"`` as the project's entry point and then describes commands,
and every one of them exited 2 on a usage line naming ``guard`` and ``mcp``. Where the MCP
server connected, the tools cover it; where it did not, the session had a working engine on
disk, a documented way to reach it, and no verb that arrived — so the fallback was guessing at
a checkout path, which is the guess this file exists to remove.

A forwarded verb keeps the resolution order and **neither of the first two rules** — the pair
``mcp`` keeps neither of, and for the same reason — because both are about the one caller that
did not ask, which is the hook that fires on every turn:

  * the plugin is not deferred to — a command somebody typed is answered whether or not some
    other surface could also have answered it, and silence is not an answer to ``pick``;
  * a missing engine is a **refusal** and not a quiet 0 — "unenforced beats broken" is the
    right trade for a hook that fires on every turn and the wrong one for a command whose
    exit code is read as its result.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
from pathlib import Path

#: The engine, relative to a roadkeep checkout — the same path :data:`roadkeep.installing.
#: LAUNCHER` names, restated here for the reason the module docstring gives.
ENGINE_REL = Path("scripts") / "roadkeep.py"

#: The name the plugin is published under, matched against the registry key's
#: ``<name>@<marketplace>`` left half.
PLUGIN = "roadkeep"

#: Where the harness keeps the registry of installed plugins, under its config directory.
REGISTRY = ("plugins", "installed_plugins.json")


def _valid(root: Path | None) -> Path | None:
    """The engine path under *root*, if the file is actually there."""
    if root is None:
        return None
    engine = root / ENGINE_REL
    return engine if engine.is_file() else None


def _config_home() -> Path:
    """The harness's config directory — ``$CLAUDE_CONFIG_DIR`` or ``~/.claude``.

    The same pair Claude Code itself resolves, and the same pair
    :func:`roadkeep.provenance.installed` resolves. A hardcoded ``~/.claude`` reads a
    directory the running harness may not be using, and on a machine with two config
    directories it then answers about the wrong install.
    """
    return Path(os.environ.get("CLAUDE_CONFIG_DIR") or Path.home() / ".claude")


def _plugin_is_wired(root: Path) -> bool:
    """Whether the harness has roadkeep enabled **for this project** (RK1108).

    The signal to stand down, and deliberately not "is there a copy on disk": a marketplace
    clone and every cached version live under ``plugins/`` whether or not this project uses
    them, so a glob for the engine finds a file in cases where no hook is loaded — and then
    this launcher defers to a plugin that never ran and nothing guards the write.

    Read defensively and never written: the file is the harness's. A registry this cannot
    parse answers False, which is the safe side of the two — a doubled deny message is
    cosmetic and an absent guard is the drift roadkeep exists to stop.

    **And a row whose install is gone is not a wired plugin** (RK1166). The harness prunes old
    versions and leaves the row behind: measured in one corpus, whose row pinned `0.1.285` while
    only three later versions were on disk, so this returned True, the launcher stood down, and
    the plugin it deferred to could not load — both guards absent at once, which is the same
    drift arriving through the reading written to stop it. An `Edit` on a governed file was not
    refused there; it reached the tool and failed on its own arguments.

    So a row that **names** an install must still have it. A row that names none is unchanged:
    there is nothing to check, and the identity claim — this project, this plugin — is what the
    row is for. The safe side stays the one already chosen: what cannot be confirmed guards.
    """
    try:
        payload = json.loads(
            _config_home().joinpath(*REGISTRY).read_text(encoding="utf-8")
        )
        wanted = root.resolve()
    except (OSError, ValueError):
        return False
    plugins = payload.get("plugins") if isinstance(payload, dict) else None
    for key, rows in (plugins if isinstance(plugins, dict) else {}).items():
        if not isinstance(key, str) or key.partition("@")[0] != PLUGIN:
            continue
        for row in rows if isinstance(rows, list) else ():
            stated = row.get("projectPath") if isinstance(row, dict) else None
            if not isinstance(stated, str) or not stated:
                continue
            try:
                # Resolved and never compared as text: the harness writes the path the way
                # its platform spells it, and a repository reached through a junction or a
                # symlink is one project written twice.
                if Path(stated).resolve() == wanted:
                    return _installed(row.get("installPath"))
            except OSError:
                continue
    return False


def _installed(stated: object) -> bool:
    """Whether the install a row names is still on disk — True where it names none (RK1166).

    The directory and not the engine inside it: what the harness prunes is the version, and a
    row pointing at a pruned one is a record of an install that ended. Checking further would
    be this file deciding whether somebody else's plugin is well-formed, which is the question
    `_valid` answers for the copy *this* launcher would run.
    """
    if not isinstance(stated, str) or not stated:
        return True  # an older harness wrote no path: nothing to check, and the row still binds
    try:
        return Path(stated).is_dir()
    except OSError:
        return False


def _repo_root() -> Path:
    """This checkout's root — ``.claude/hooks/roadkeep-launch.py`` up three."""
    stated = os.environ.get("CLAUDE_PROJECT_DIR")
    return Path(stated) if stated else Path(__file__).resolve().parents[2]


def _cache_engine() -> Path | None:
    """A clone something else populated. Never created here — see the third rule."""
    base = os.environ.get("XDG_CACHE_HOME") or (Path.home() / ".cache")
    return _valid(Path(base) / "roadkeep-src" / "roadkeep")


def _resolve() -> Path | None:
    """An engine to run, or None. Not the deferral check — that is asked first and separately."""
    home = os.environ.get("ROADKEEP_HOME")
    return (
        (_valid(Path(home)) if home else None)
        or _valid(_repo_root().parent / "roadkeep")
        or _cache_engine()
    )


def _guard(argv: list[str], payload: bytes | None) -> int:
    """The hook, which is the caller both of the first two rules are about."""
    if _plugin_is_wired(_repo_root()):
        return 0  # the plugin's own hook already runs; do not double-fire.
    engine = _resolve()
    if engine is None:
        return 0  # unenforced beats broken.
    return subprocess.run(
        [sys.executable, str(engine), "guard", *argv], input=payload, check=False
    ).returncode


def _serve(argv: list[str]) -> int:
    """The server, which stands down for nobody (RK1189) — see the module docstring.

    A wired plugin is not asked about: two ``roadkeep`` servers are two entries the harness
    reads separately, and the one thing an exit here cannot mean is *another copy answers*.
    """
    engine = _resolve()
    if engine is None:
        return _missing()
    # `execv`, so the server owns this process's stdio rather than talking through a pipe to a
    # parent that would have to shuttle every frame.
    os.execv(sys.executable, [sys.executable, str(engine), "mcp", *argv])


def _missing() -> int:
    """No engine, for a caller whose exit code is read as its result.

    Named rather than silent, and it names the two things a caller can do. ASCII, like the line
    it replaces: this file writes to whatever console the environment gave it, and a refusal
    that raises on encoding is a refusal that arrives as a traceback.
    """
    sys.stderr.write(
        "roadkeep-launch.py: no engine found: set ROADKEEP_HOME to a roadkeep "
        "checkout, or put one beside this repository as ../roadkeep\n"
    )
    return 2


def _forward(argv: list[str]) -> int:
    """Every other verb, run against the engine this file resolves (RK1116).

    No payload is read here, unlike ``guard``: a stream is readable once and the child is the
    one that wants it, so stdin is inherited and `add --why -` reaches the engine's own reader.
    """
    engine = _resolve()
    if engine is None:
        return _missing()
    return subprocess.run([sys.executable, str(engine), *argv], check=False).returncode


def main(argv: list[str]) -> int:
    if argv[:1] == ["guard"]:
        # The payload is read here and handed on, because a stream is readable once.
        return _guard(argv[1:], sys.stdin.buffer.read())
    if argv[:1] == ["mcp"]:
        return _serve(argv[1:])
    # Including no arguments at all, which the engine answers with its own usage and its own
    # list of verbs — a better answer than a line naming the two modes nobody types.
    return _forward(argv)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
