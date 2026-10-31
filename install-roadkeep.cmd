@echo off
setlocal

REM ============================================================================
REM install-roadkeep.cmd - Vendor the roadkeep engine into .roadkeep/, the way
REM `npm install` fills node_modules.
REM
REM roadkeep owns docs/ROADMAP.md, docs/CHANGELOG.md and docs/IMPROVEMENTS.md, so
REM a session runs it constantly. Two things go wrong when it is run from
REM wherever it happens to be found:
REM
REM   * Every distinct command path is a fresh authorization prompt. A copy under
REM     a plugin cache or a scratch directory changes path per version and per
REM     machine, so the allowance never sticks. `.roadkeep\scripts\roadkeep.py`
REM     is one stable path, allow-listed once in .claude\settings.json.
REM   * Which copy answers is not decidable by looking. This machine carries two
REM     plugin caches at different versions plus a marketplace clone, and a live
REM     checkout can be mid-refactor - a session lost time to an ImportError from
REM     a half-edited working tree.
REM
REM ROADKEEP_HOME (.claude\settings.json) points at .roadkeep, which is the
REM launcher's own first resolution step, so the guard hook, the MCP server and a
REM hand-run command all reach the same engine.
REM
REM .roadkeep\ is git-ignored: reproduced by this script, never committed.
REM
REM The source is chosen by VERSION, never by position - candidates are asked
REM `--version` and the highest wins. Set ROADKEEP_SRC to force one.
REM
REM Written in Python, and NEITHER this repository nor freeharbor is a Python
REM project - Shio is Java, freeharbor is .NET. Python is required by roadkeep
REM itself: .claude\settings.json already runs its guard hook as `python ...`.
REM So this adds no runtime that the thing being installed did not already
REM force, where a Node implementation would have.
REM
REM Usage:
REM   install-roadkeep.cmd            Vendor the newest engine found.
REM   install-roadkeep.cmd --check    Report only; exit 1 when out of date.
REM
REM See also update-roadkeep.cmd, which updates the PLUGIN from its marketplace.
REM Run that first when you want a newer engine than any cache holds, then this.
REM ============================================================================

where python >nul 2>nul
if errorlevel 1 (
    echo [ERROR] `python` is not on PATH. roadkeep is a Python tool - the same
    echo         interpreter its guard hook in .claude\settings.json already uses.
    exit /b 1
)

python "%~dp0scripts\install_roadkeep.py" %*
exit /b %errorlevel%
