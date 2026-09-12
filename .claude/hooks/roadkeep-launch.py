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

  1. ``$ROADKEEP_HOME/scripts/roadkeep.py``          an explicit override, and where it is
                                                      set the only one (RK1678)
  2. a vendored ``.roadkeep/``                        the copy the project chose (RK1193)
  3. a sibling checkout ``../roadkeep``              two repositories cloned side by side
  4. a cached clone under the user cache directory   the web, second turn onward

The last three are *found*, so the first of them that runs answers. The first is *named*, so
nothing else answers for it: a copy nobody chose, answering where the named one could not, is
indistinguishable from the named one answering (see :func:`_candidates`).

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
import re
import subprocess
import sys
from pathlib import Path

#: The engine, relative to a roadkeep checkout — the same path :data:`roadkeep.installing.
#: LAUNCHER` names, restated here for the reason the module docstring gives.
ENGINE_REL = Path("scripts") / "roadkeep.py"

#: Where `roadkeep install --vendor` puts a pinned engine, relative to the repository root
#: (RK1193). Restated here rather than imported for this file's standing reason: it runs
#: before an engine has been found, so it may not import the package that names it, and
#: `tests/test_launching.py` holds the two spellings together.
VENDORED = ".roadkeep"

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
    """An engine to run, or None. Not the deferral check — that is asked first and separately.

    ``.roadkeep/`` is second and it is the whole of RK1193 as this file sees it: a copy the
    project vendored is a copy the project *chose*, so it outranks whatever a sibling clone
    happens to be today and needs no environment variable to be honoured. Below
    ``$ROADKEEP_HOME``, which stays the explicit override — a caller who names a tree has said
    which one they mean, and a pin nobody can step over for one command is a pin that gets
    deleted instead of used.
    """
    return next(iter(_candidates()), None)


def _named() -> Path | None:
    """The tree ``$ROADKEEP_HOME`` names, expanded, or None where it names none (RK1678).

    Empty is none, which is how a settings file or a caller says *not set* while keeping the
    key. A variable nothing resolves is a name all the same (see :func:`_expanded`).
    """
    home = _expanded(os.environ.get("ROADKEEP_HOME"))
    return Path(home) if home else None


def _candidates() -> list[Path]:
    """Every engine this file can reach, in resolution order (RK1214).

    Split out of :func:`_resolve` because *existing* turned out to be the wrong test and a
    caller that needs the next one has to be able to ask for it. The order is unchanged and is
    the whole of RK1193 and RK1200 as this file sees them.

    **A named engine is the whole list** (RK1678). `$ROADKEEP_HOME` was the first candidate
    and was dropped by the same tests as the rest — absent, or failing its probe — so a
    checkout mid-save fell through to whatever answered next. Measured on one machine: three
    commands served by `0.2.4` out of the user cache while the sibling stood at `0.2.450`, and
    setting the variable changed nothing, because it was a candidate like the others. Falling
    through is right for a copy found on disk and wrong for one somebody named: the next copy
    is one nobody chose, and its answer reads exactly like the named one's. So where it is set
    nothing else is tried, and :func:`_missing` names it — which for `guard` means
    *unenforced*, the second rule being the guard's and this not overriding it.
    """
    named = _named()
    if named is not None:
        engine = _valid(named)
        return [] if engine is None else [engine]
    found = [
        _valid(_repo_root() / VENDORED),
        _valid(_repo_root().parent / "roadkeep"),
        _cache_engine(),
    ]
    return [one for one in found if one is not None]


#: Seconds an engine may take to say what it is (RK1214). Generous for a cold import on a slow
#: disk, and short enough that a caller does not read the probe as a hang.
PROBE_TIMEOUT = 30


def _answers(engine: Path) -> bool:
    """Whether this engine **runs**, asked by running it — because existing is not that.

    Measured in pportal mid-task: a `section add` that had worked four times in the same minute
    came back as `ImportError: cannot import name NotOpen from roadkeep.backlog`. The engine
    resolved was a sibling checkout part-way through a refactor. Nothing was written, and the
    roadmap was left holding a line whose section did not exist — the gate red, mid-task.

    This file's own rules make that a defect rather than bad luck: *never block a turn*, and a
    missing roadkeep degrades to unenforced and never to a broken session. **A checkout
    mid-refactor is not missing.** It is found, chosen, and then it explodes — the failure that
    rule exists to prevent, arriving through the one door it does not cover.

    `--version` and not an import: this file may not import :mod:`roadkeep` (see the module
    docstring), and a subprocess is also the only thing that proves the *child* will start,
    which is what every mode here goes on to spawn.
    """
    return not _probe(engine)


#: What each probe this process ran said, by engine — `""` where it answered (RK1678). Kept so
#: a refusal quotes the probe that decided and never runs a second one: on a checkout mid-save,
#: the one state this is about, asking again can answer differently.
_PROBED: dict[Path, str] = {}

#: How much of an engine's own sentence a refusal quotes. A traceback's last line fits, and an
#: engine that prints one enormous line is not one whose every byte this file should repeat.
_SAID_MAX = 200


def _probe(engine: Path) -> str:
    """Why this engine does not run, or `""` where it answered — :func:`_answers` with the why.

    Three ways not to answer, and the refusal names which: it exited, it did not answer in
    time, or it could not be started at all.
    """
    try:
        done = subprocess.run(
            [sys.executable, str(engine), "--version"],
            capture_output=True,
            timeout=PROBE_TIMEOUT,
            check=False,
        )
    except subprocess.TimeoutExpired:
        why = f"did not answer --version within {PROBE_TIMEOUT}s"
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        why = f"could not be started ({error})"
    else:
        why = "" if done.returncode == 0 else f"exited {done.returncode} on --version"
        said = _said(done.stderr) if why else ""
        if said:
            why = f"{why}: {said}"
    _PROBED[engine] = why
    return why


def _said(stderr: bytes | None) -> str:
    """The line of an engine's stderr that says what went wrong (RK1678).

    **The last one at column 0.** A Python traceback indents its frames and leaves the header
    and the exception flush left, so the last unindented line is `ImportError: …`; the engine's
    own refusal for a checkout that does not parse (RK1179) indents its rows under a first line
    that says so. One structural rule for both, and never a match on an error's name.
    """
    lines = [
        one for one in (stderr or b"").decode("utf-8", "replace").splitlines() if one.strip()
    ]
    flush = [one for one in lines if not one[0].isspace()]
    said = (flush or lines or [""])[-1].strip()
    return said if len(said) <= _SAID_MAX else said[: _SAID_MAX - 3] + "..."


def _running() -> Path | None:
    """The first candidate that answers, or None (RK1214).

    Paid where a broken engine would half-do something that cannot be taken back: a forwarded
    verb may **write**, and on POSIX the server is `execv`'d. One probe on a healthy machine,
    because the first candidate answers.

    Not paid by :func:`_guard`, which is the hot path and needs no probe at all: it writes
    nothing, so it can simply run a candidate and try the next on any failure. **Nor by the
    server on Windows** (RK1465), where there is no image to replace: the parent waits on the
    child, so the failure this predicts arrives as that child's exit code — and the probe is a
    second cold Python start on the one path a client puts a thirty-second ceiling on.
    """
    return next((one for one in _candidates() if _answers(one)), None)


#: A variable reference in a settings value, in both spellings a file may carry (RK1200).
#: Two patterns rather than one conditional group, because this file is read by people
#: debugging a hook and a back-reference in a regex is not what they should have to parse.
_BRACED = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}")
_BARE = re.compile(r"\$([A-Za-z_][A-Za-z0-9_]*)")


def _expanded(value: str | None) -> str | None:
    """``ROADKEEP_HOME`` with its variables resolved, or it unchanged (RK1200).

    The harness passes ``env`` values through **verbatim**, and the spelling a project
    naturally reaches for is the one ``install`` writes into every hook ``command`` in the very
    same file::

        "env": { "ROADKEEP_HOME": "${CLAUDE_PROJECT_DIR}/.roadkeep" }

    Measured on an adopting project: the braces arrived intact, ``Path(home)`` named nothing,
    and resolution fell through to the sibling — a neighbour's working tree, a version ahead
    and mid-refactor for part of a session, during which the guard denying hand edits of the
    governed files was running a traceback. Nothing said so at any point, because a second
    candidate answering is indistinguishable from a project that meant to use it.

    ``CLAUDE_PROJECT_DIR`` is answered from :func:`_repo_root` and not only from the
    environment, which is the half a plain ``expandvars`` gets wrong: the harness interpolates
    it into the command line without necessarily exporting it, so the name a settings file
    writes can be one this process cannot look up — and this file already knows the answer.

    A variable nothing resolves is left **as written** rather than emptied. Both fail, and they
    fail differently: `${NOPE}/.roadkeep` names a tree that is not there and is refused by that
    name (RK1678), while an empty expansion is `/.roadkeep`, a path at the filesystem root that
    could exist and would then be run. The silent-wrong-engine outcome is the one this whole
    task is about.
    """
    if not value:
        return value
    known = {"CLAUDE_PROJECT_DIR": str(_repo_root())}

    def answer(match: "re.Match[str]") -> str:
        name = match.group(1)
        return known.get(name) or os.environ.get(name) or match.group(0)

    return _BARE.sub(answer, _BRACED.sub(answer, value))


def _guard(argv: list[str], payload: bytes | None) -> int:
    """The hook, which is the caller both of the first two rules are about.

    **Falls through on any failure** (RK1214), and needs no probe to do it safely: `guard`
    writes nothing and the CLI returns 0 on every path it has, so a non-zero exit here means
    the engine did not work rather than that the hook decided something. Trying the next
    candidate is therefore free of the one hazard a retry can carry — there is no half-done
    write to repeat — and the last word is still 0, which is this file's own rule.

    Optimistic rather than probed, because this is the path that runs on every tool call in
    every session: a healthy machine pays exactly what it paid before, and only a broken
    engine pays for a second spawn.

    The fall-through is over what was **found** (RK1678). A named engine is the whole list, so
    one that fails leaves the turn unenforced rather than guarded by a copy nobody chose.
    """
    if _plugin_is_wired(_repo_root()):
        return 0  # the plugin's own hook already runs; do not double-fire.
    for engine in _candidates():
        done = subprocess.run(
            [sys.executable, str(engine), "guard", *argv], input=payload, check=False
        )
        if done.returncode == 0:
            return 0
    return 0  # unenforced beats broken, whether none was found or none of them ran.


def _windows() -> bool:
    """Whether this platform spawns where POSIX replaces the image (RK1446, RK1465).

    A function and not two `os.name` reads: `_serve` branches on it twice — once for whether
    the probe is bought and once for how the child is run — and those are one fact. It is also
    the seam a test can move, which patching `os.name` is not: `pathlib` reads that name too,
    and a test that changed it stopped being able to make a path.
    """
    return os.name == "nt"


def _serve(argv: list[str]) -> int:
    """The server, which stands down for nobody (RK1189) — see the module docstring.

    A wired plugin is not asked about: two ``roadkeep`` servers are two entries the harness
    reads separately, and the one thing an exit here cannot mean is *another copy answers*.

    Probed (RK1214), because `execv` cannot be taken back: a broken engine here replaces this
    process and the harness reads the exit as a crashed server, in the projects where
    everything was installed correctly.

    **And `execv` is POSIX's answer, not Windows'** (RK1446). There is no image to replace
    there: the CRT's `_execv` *spawns* and lets the caller exit, and what the harness spawned
    is the caller. Measured on Windows 11 from a project whose `.mcp.json` is the one `install`
    writes — `roadkeep-launch.py mcp` returned 0 in under a second with both streams empty,
    while the engine invoked directly was still serving at ten seconds. No server survived the
    launcher, so the harness held a pipe nothing was on, reported `CONNECT_TIMEOUT` after
    thirty seconds and dropped every tool; the hooks still fired, so the session was told to
    call tools it had never been handed and a hand-edit was denied with a command it could not
    run. The exit code is the sharpest part: a 2 would have surfaced as a failed server, and 0
    in silence is indistinguishable from one that started and closed cleanly.

    So there the child is run to completion with this process's stdio **inherited** — one
    extra process in the chain, and still no pipe in the middle, which is the property the
    `execv` was for. The parent does nothing but wait and hand back the exit code.

    **And there the probe is not paid** (RK1465). It exists because `execv` cannot be taken
    back: a broken engine replaces this process and the harness reads the exit as a crashed
    server. With no image replaced there is nothing to take back — the parent is still here
    when the child fails, and the failure the probe predicts has already happened in front of
    it, with the child's own exit code standing for it.

    Measured on Windows 11 against this checkout, five runs each, `initialize` written to
    stdin and the first response line read back: the engine directly at 283 ms min and 315
    median, this file at 646 and 677. The difference is one `python scripts/roadkeep.py
    --version` — a whole interpreter start and a whole `roadkeep.cli` import, 287 ms of the
    363 this file added. What that costs is not the milliseconds: connecting is the engine's
    time plus a second cold start, on the one path a client puts a thirty-second ceiling on.
    RK1449 moved the cliff out of `initialize`, and this is the floor under it.
    """
    # `_resolve` and not `_running` on the branch that waits: see above. On POSIX the probe
    # stays, `execv` being the thing it was bought for.
    waits = _windows()
    engine = _resolve() if waits else _running()
    if engine is None:
        return _missing()
    command = [sys.executable, str(engine), "mcp", *argv]
    if waits:
        # `_forward`'s call exactly, and for the same reason it is spelled that way: no
        # `stdout`, `stderr` or `stdin` argument, so all three are this process's own.
        return subprocess.run(command, check=False).returncode
    # `execv`, so the server owns this process's stdio rather than talking through a pipe to a
    # parent that would have to shuttle every frame.
    os.execv(sys.executable, command)


def _missing() -> int:
    """No engine, for a caller whose exit code is read as its result.

    Named rather than silent, and it names the two things a caller can do. ASCII, like the line
    it replaces: this file writes to whatever console the environment gave it, and a refusal
    that raises on encoding is a refusal that arrives as a traceback.

    **"None that runs" and not "none on disk"** (RK1214). The two are one sentence here because
    they are one outcome for this caller, and telling them apart is what the second clause is
    for: a candidate that exists and does not answer is the case that used to arrive as an
    `ImportError` from inside somebody's half-refactored checkout, and a message that said
    *no engine found* over a directory plainly sitting there would send its reader looking for
    the wrong thing.

    **And a named engine gets its own sentence** (RK1678): see :func:`_unrunnable`.
    """
    named = _named()
    if named is not None:
        return _unrunnable(named)
    seen = len(_candidates())
    found = (
        f"{seen} candidate(s) on disk, none of which ran"
        if seen
        else "no engine found"
    )
    sys.stderr.write(
        f"roadkeep-launch.py: {found}: set ROADKEEP_HOME to a roadkeep "
        "checkout, or put one beside this repository as ../roadkeep\n"
    )
    return 2


def _unrunnable(named: Path) -> int:
    """The engine ``$ROADKEEP_HOME`` names cannot be run, and nothing else was tried (RK1678).

    Three facts, being what a reader acts on: which variable chose it, the path it resolved to,
    and what the probe said — a tree holding no engine, one that exited, one that did not
    answer in time. The probe quoted is the one that decided (:data:`_PROBED`), and a named
    engine that is on disk only reaches here after one: the server on Windows, which buys no
    probe, runs the named engine and lets its own exit say it failed.

    And what was **not** done, said because it is the question a working sibling beside a
    refusal raises: no other copy was tried, the name being the choice.

    ASCII, like :func:`_missing`, and folded rather than trusted to be: a path and an engine's
    own stderr can carry any codepoint, and a console that cannot encode one turns this
    refusal into a traceback.
    """
    engine = _valid(named)
    if engine is None:
        where, why = named, f"holds no {ENGINE_REL.as_posix()}"
    else:
        where, why = engine, _PROBED.get(engine) or "did not answer"
    message = (
        f"roadkeep-launch.py: ROADKEEP_HOME names {where}, which {why}: a named engine is the "
        "one meant, so no other copy was tried - fix that tree, or unset ROADKEEP_HOME to let "
        "the launcher find one\n"
    )
    sys.stderr.write(message.encode("ascii", "backslashreplace").decode("ascii"))
    return 2


def _forward(argv: list[str]) -> int:
    """Every other verb, run against the engine this file resolves (RK1116).

    No payload is read here, unlike ``guard``: a stream is readable once and the child is the
    one that wants it, so stdin is inherited and `add --why -` reaches the engine's own reader.

    Probed and never retried (RK1214), which is the split this task turns on: a forwarded verb
    may **write**, so running one and trying the next on failure could repeat a half-done
    write — the one hazard `guard`'s fall-through does not have. Asking first costs one spawn
    on a path a person typed, and it is the path the measured incident came through: a `section
    add` that had worked four times in the same minute returned an `ImportError` out of a
    checkout mid-refactor, wrote nothing, and left the roadmap pointing at a section that did
    not exist.
    """
    engine = _running()
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
