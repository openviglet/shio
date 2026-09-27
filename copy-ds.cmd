@echo off
REM Try an unpublished design-system change in this console: forwards to the design
REM system's own `pnpm use:local`, scoped to this checkout's shio-react.
REM
REM The design system resolves its consumers from a register (VDS130), so this wrapper
REM owns exactly one fact: where the design system's checkout is. It lives beside the
REM products as git worktrees, one per version, with a `latest` junction pointing at the
REM one in use -- the same stable pointer Shio and Turing use. Resolving through that
REM junction is SH948: the plain sibling path now reaches the worktree container, which
REM has no scripts, and the wrapper reported a missing package instead of a broken path.
REM
REM Set VIGLET_DS to use another checkout. `copy-ds.cmd --list` names the consumers.
setlocal

if defined VIGLET_DS (
    set "DS_SRC=%VIGLET_DS%"
) else (
    set "DS_SRC=%~dp0..\..\viglet-design-system\latest"
)

if not exist "%DS_SRC%\scripts\use-local.mjs" (
    echo Could not find the design system checkout at "%DS_SRC%".
    echo Expected the viglet-design-system `latest` worktree junction beside the products,
    echo or set VIGLET_DS to a checkout. `pnpm use:local` can also be run from it directly.
    exit /b 1
)

pushd "%DS_SRC%"
call pnpm use:local "%~dp0shio-react" %*
set RESULT=%ERRORLEVEL%
popd

exit /b %RESULT%
