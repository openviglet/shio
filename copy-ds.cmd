@echo off
REM Deprecated wrapper. This script used to build the design system and xcopy its
REM dist into a hardcoded path -- d:\git\viglet\shio\shio-react\node_modules -- which
REM stopped existing when the checkout moved to 2026.3, so it silently did nothing
REM useful for two version bumps.
REM
REM The design system now owns the recipe and resolves both the consumers and the
REM install location instead of hardcoding them. Run it from there directly:
REM
REM     cd d:\Git\viglet\viglet-design-system
REM     pnpm use:local
REM
REM This wrapper just forwards to it, scoped to this checkout.
setlocal

set DS_SRC=%~dp0..\..\viglet-design-system

if not exist "%DS_SRC%\scripts\use-local.mjs" (
    echo Could not find the design system checkout at "%DS_SRC%".
    echo Clone it beside the products, or run `pnpm use:local` from it directly.
    exit /b 1
)

pushd "%DS_SRC%"
call pnpm use:local "%~dp0shio-react" %*
set RESULT=%ERRORLEVEL%
popd

exit /b %RESULT%
