@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM publish-js.cmd - Build and publish the Viglet Shio JavaScript packages to
REM npm DIRECTLY from your machine, without the GitHub Actions workflow
REM (.github/workflows/publish-js-sdk.yml). Same packages, same order, same
REM versioning rule - so a local release and a CI release are the same release.
REM
REM Packages (all under the @viglet scope, workspace roots at the repo root):
REM   shio-model        -^> @viglet/shio-model        (post-types DSL; no deps)
REM   shio-sections     -^> @viglet/shio-sections     (section vocabulary; on the DSL)
REM   shio-client       -^> @viglet/shio-client       (framework-agnostic CDA core)
REM   shio-react-sdk    -^> @viglet/shio-react-sdk    (React hooks/components)
REM   shio-js-sdk       -^> @viglet/shio-editor-cors  (Universal Editor bridge)
REM   create-shio-app   -^> @viglet/create-shio-app   (Next.js starter generator)
REM   cli               -^> @viglet/shio              (the CLI)
REM
REM Auth (publish to registry.npmjs.org):
REM   Either be `npm login`-ed already, OR set a token:
REM     set NPM_TOKEN=npm_xxxxxxxxxxxxxxxxxxxx
REM   When NPM_TOKEN is set it is exported as NODE_AUTH_TOKEN and a temporary
REM   .npmrc carries the auth line for the publish steps only. Deleted at the end.
REM
REM ---------------------------------------------------------------------------
REM ONE VERSION FOR THE WHOLE WORKSPACE
REM
REM The bump is NOT per package. The root package.json is bumped once and the
REM resulting string is stamped into every workspace package - publishable and
REM private alike - exactly as the workflow does it.
REM
REM Bumping each package with its own `npm version patch` (the obvious form, and
REM the one the workflow used to have) increments but does not converge: each
REM package moves from its OWN current value, so any drift is permanent. It is
REM also what a partial publish produces, because a run that dies after three
REM packages leaves those three on npm and the repo behind. Stamping one computed
REM version cannot do that: re-running it is idempotent, and a package that had
REM drifted is pulled back into line by the next release rather than never.
REM
REM Publishing a SUBSET still bumps the whole workspace. That is deliberate - the
REM invariant is "every package carries the same version", not "every package is
REM on npm at that version", which was never true anyway.
REM ---------------------------------------------------------------------------
REM
REM Usage:
REM   publish-js.cmd                        Bump + publish ALL packages.
REM   publish-js.cmd client                 Only @viglet/shio-client.
REM   publish-js.cmd model sections         A chosen subset.
REM   publish-js.cmd --no-bump [targets]    Publish the current version as-is.
REM   publish-js.cmd --dry-run [targets]    Do everything except publish.
REM
REM Target names: model sections client react-sdk editor-cors create-app cli
REM
REM Notes:
REM   * Publish order is load-bearing. `pnpm publish` rewrites `workspace:*` to
REM     the dependency's CURRENT version, so a package must be published AFTER
REM     the packages it depends on - otherwise it ships pointing at a version
REM     that does not exist yet. model -^> sections, client -^> react-sdk,
REM     client+create-app -^> cli.
REM   * Every package with a `test` script runs it as a publish gate.
REM   * `pnpm publish` and not `npm publish`: npm leaves `workspace:*` verbatim
REM     and produces a package that cannot be installed ("Unsupported URL Type").
REM ============================================================================

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

REM --- Resolve pnpm: prefer the copy frontend-maven-plugin installed (correct ---
REM     version, no corepack self-download) over a PATH shim that tries to switch
REM     versions. Do NOT prepend shio-react\node to PATH - its npm.cmd is a stub
REM     with no npm-cli.js and would shadow the real global npm, which this script
REM     needs for `npm version`.
set "npm_config_manage_package_manager_versions=false"
set "npm_config_verify_deps_before_run=false"
if exist "%ROOT%\shio-react\node\pnpm.cmd" (
    set "PNPM=%ROOT%\shio-react\node\pnpm.cmd"
) else (
    set "PNPM=pnpm"
)

REM --- Parse args ------------------------------------------------------------
set "DO_BUMP=1"
set "DRY_RUN=0"
set "WANT_MODEL=0"
set "WANT_SECTIONS=0"
set "WANT_CLIENT=0"
set "WANT_REACTSDK=0"
set "WANT_EDITOR=0"
set "WANT_CREATEAPP=0"
set "WANT_CLI=0"
set "ANY_TARGET=0"

:parse
if "%~1"=="" goto after_parse
if /i "%~1"=="--no-bump"    ( set "DO_BUMP=0"        & shift & goto parse )
if /i "%~1"=="--dry-run"    ( set "DRY_RUN=1"        & shift & goto parse )
if /i "%~1"=="model"        ( set "WANT_MODEL=1"     & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="sections"     ( set "WANT_SECTIONS=1"  & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="client"       ( set "WANT_CLIENT=1"    & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="react-sdk"    ( set "WANT_REACTSDK=1"  & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="editor-cors"  ( set "WANT_EDITOR=1"    & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="create-app"   ( set "WANT_CREATEAPP=1" & set "ANY_TARGET=1" & shift & goto parse )
if /i "%~1"=="cli"          ( set "WANT_CLI=1"       & set "ANY_TARGET=1" & shift & goto parse )
echo [ERROR] Unknown argument: %~1
echo         Valid targets: model ^| sections ^| client ^| react-sdk ^| editor-cors ^| create-app ^| cli
echo         Flags: --no-bump ^| --dry-run
exit /b 1

:after_parse
if "%ANY_TARGET%"=="0" (
    set "WANT_MODEL=1"
    set "WANT_SECTIONS=1"
    set "WANT_CLIENT=1"
    set "WANT_REACTSDK=1"
    set "WANT_EDITOR=1"
    set "WANT_CREATEAPP=1"
    set "WANT_CLI=1"
)

echo ============================================================
echo  Publishing Viglet Shio JS packages to npm
echo  model       (@viglet/shio-model)       : %WANT_MODEL%
echo  sections    (@viglet/shio-sections)    : %WANT_SECTIONS%
echo  client      (@viglet/shio-client)      : %WANT_CLIENT%
echo  react-sdk   (@viglet/shio-react-sdk)   : %WANT_REACTSDK%
echo  editor-cors (@viglet/shio-editor-cors) : %WANT_EDITOR%
echo  create-app  (@viglet/create-shio-app)  : %WANT_CREATEAPP%
echo  cli         (@viglet/shio)             : %WANT_CLI%
echo  workspace-wide bump                    : %DO_BUMP% ^(1=yes^)
echo  dry run ^(build+test, never publish^)    : %DRY_RUN% ^(1=yes^)
echo ============================================================

REM --- Install workspace deps (frozen=false, like the workflow) ---------------
echo.
echo [INFO] Installing workspace deps ...
call "%PNPM%" -C "%ROOT%" install --frozen-lockfile=false
if errorlevel 1 ( echo [ERROR] pnpm install failed. & exit /b 1 )

REM --- One version for the whole workspace ------------------------------------
if "%DO_BUMP%"=="1" (
    echo.
    echo [INFO] Bumping the root and stamping every workspace package ...
    pushd "%ROOT%"
    call npm version patch --no-git-tag-version
    if errorlevel 1 ( popd & echo [ERROR] root npm version patch failed. & exit /b 1 )
    popd
    call :stamp || exit /b 1

    REM The bump changes what `emit` composes. `emit.mjs` writes
    REM `rangeFor(name) ?? ^<workspace version>` for a needed package, and while
    REM @viglet/shio-sections is unpublished rangeFor answers null - so the emitted
    REM range IS the workspace version, which just moved. Skipping this leaves the
    REM three blueprint packages stale and emit.test.mjs red on a drift nobody
    REM caused by editing anything.
    echo [INFO] Re-emitting the blueprint packages ...
    call "%PNPM%" -C "%ROOT%\create-shio-app" run emit
    if errorlevel 1 ( echo [ERROR] emit failed. & exit /b 1 )
)

call :read_version || exit /b 1
echo [INFO] Releasing version %VERSION%
call :assert_one_version || exit /b 1

REM --- Auth: a temp .npmrc, exposed only AFTER the install so the install ------
REM     still uses the real ~/.npmrc. npm_config_userconfig is honoured by both
REM     npm and pnpm.
set "NPMRC_TMP="
if not "%NPM_TOKEN%"=="" (
    set "NPMRC_TMP=%TEMP%\shio-publish-%RANDOM%%RANDOM%.npmrc"
    >  "!NPMRC_TMP!" echo registry=https://registry.npmjs.org/
    >> "!NPMRC_TMP!" echo //registry.npmjs.org/:_authToken=%NPM_TOKEN%
    >> "!NPMRC_TMP!" echo @viglet:registry=https://registry.npmjs.org/
    >> "!NPMRC_TMP!" echo always-auth=true
    set "npm_config_userconfig=!NPMRC_TMP!"
    set "NODE_AUTH_TOKEN=%NPM_TOKEN%"
    echo [INFO] Using NPM_TOKEN for npm authentication ^(temp .npmrc^).
) else (
    echo [INFO] NPM_TOKEN not set - assuming an existing `npm login`.
)

REM ============================================================================
REM  Publish order is dependency order. See the header: pnpm rewrites
REM  workspace:* to the dependency's current version at pack time, so a package
REM  published before the thing it depends on ships a dangling range.
REM ============================================================================

REM -- shio-model: the DSL, nothing depends on it yet -------------------------
if "%WANT_MODEL%"=="1" (
    echo.
    echo --- @viglet/shio-model ------------------------------------------------
    call :build  "@viglet/shio-model"                 || exit /b 1
    call :gate   "shio-model"                         || exit /b 1
    call :publish "shio-model" "@viglet/shio-model"   || exit /b 1
)

REM -- shio-sections: declares workspace:* on the DSL above -------------------
if "%WANT_SECTIONS%"=="1" (
    echo.
    echo --- @viglet/shio-sections ---------------------------------------------
    call :build  "@viglet/shio-sections"                     || exit /b 1
    call :gate   "shio-sections"                             || exit /b 1
    call :publish "shio-sections" "@viglet/shio-sections"    || exit /b 1
)

REM -- shio-client: the CDA core, no workspace deps ---------------------------
if "%WANT_CLIENT%"=="1" (
    echo.
    echo --- @viglet/shio-client -----------------------------------------------
    call :build  "@viglet/shio-client"                  || exit /b 1
    call :gate   "shio-client"                          || exit /b 1
    call :publish "shio-client" "@viglet/shio-client"   || exit /b 1
)

REM -- shio-react-sdk: declares workspace:* on the client above ---------------
if "%WANT_REACTSDK%"=="1" (
    echo.
    echo --- @viglet/shio-react-sdk --------------------------------------------
    call :build  "@viglet/shio-react-sdk"                       || exit /b 1
    call :gate   "shio-react-sdk"                               || exit /b 1
    call :publish "shio-react-sdk" "@viglet/shio-react-sdk"     || exit /b 1
)

REM -- shio-editor-cors: standalone in both directions ------------------------
if "%WANT_EDITOR%"=="1" (
    echo.
    echo --- @viglet/shio-editor-cors ------------------------------------------
    call :build  "@viglet/shio-editor-cors"                     || exit /b 1
    call :gate   "shio-js-sdk"                                  || exit /b 1
    call :publish "shio-js-sdk" "@viglet/shio-editor-cors"      || exit /b 1
)

REM -- create-shio-app: nothing to build, the template is carried as files ----
if "%WANT_CREATEAPP%"=="1" (
    echo.
    echo --- @viglet/create-shio-app -------------------------------------------
    call :gate   "create-shio-app"                                  || exit /b 1
    call :publish "create-shio-app" "@viglet/create-shio-app"       || exit /b 1
)

REM -- cli LAST: declares workspace:* on the client AND the generator ---------
if "%WANT_CLI%"=="1" (
    echo.
    echo --- @viglet/shio ------------------------------------------------------
    call :gate   "cli"                          || exit /b 1
    call :publish "cli" "@viglet/shio"          || exit /b 1
)

call :cleanup
echo.
if "%DRY_RUN%"=="1" (
    echo [OK] Dry run complete at %VERSION% - nothing was published.
) else (
    echo [OK] Published %VERSION%.
    echo      Next: update create-shio-app\src\published.mjs so the scaffolds may
    echo      declare ^^%VERSION%, and commit the workspace-wide version bump.
)
endlocal
exit /b 0

REM ===========================================================================
REM  Stamp the root's version into every workspace package.
REM  --allow-same-version is what makes it idempotent: the root is already there.
:stamp
call :read_version || exit /b 1
call "%PNPM%" -C "%ROOT%" -r --include-workspace-root exec -- npm version %VERSION% --no-git-tag-version --allow-same-version
if errorlevel 1 ( echo [ERROR] stamping the workspace version failed. & exit /b 1 )
exit /b 0

REM ---------------------------------------------------------------------------
REM  Read the root package.json version into %VERSION%.
:read_version
set "VERSION="
for /f "usebackq delims=" %%v in (`node -p "require('%ROOT:\=/%/package.json').version"`) do set "VERSION=%%v"
if "%VERSION%"=="" ( echo [ERROR] could not read the root version. & exit /b 1 )
exit /b 0

REM ---------------------------------------------------------------------------
REM  The invariant, before anything is published. Reads the workspace from pnpm
REM  rather than from a list in this file - a package added tomorrow is covered,
REM  which is how @viglet/shio-editor-cors sat outside the release for two
REM  versions without anything saying so.
:assert_one_version
set "WSJSON=%TEMP%\shio-ws-versions.json"
call "%PNPM%" -C "%ROOT%" ls -r --depth -1 --json > "%WSJSON%" 2>nul
REM  Not one exclamation mark in the node source below, and that is not style:
REM  `setlocal enabledelayedexpansion` is on for the temp-.npmrc block, and it eats
REM  `!` inside a quoted argument. `p.version!==want` reached node as `p.version`
REM  with the rest swallowed - a SyntaxError that read like a broken assert rather
REM  than a quoting bug. Hence `===want?false:true` and `length===0`.
node -e "const fs=require('fs');const want=require('%ROOT:\=/%/package.json').version;const off=JSON.parse(fs.readFileSync(process.argv[1],'utf8')).filter(p=>p.version===want?false:true);if(off.length===0){console.log('[INFO] all workspace packages at '+want);process.exit(0);}for(const p of off)console.error('[ERROR] '+p.name+' is at '+p.version+', not '+want);process.exit(1);" "%WSJSON%"
set "RC=%errorlevel%"
del /q "%WSJSON%" 2>nul
if not "%RC%"=="0" (
    echo [ERROR] the workspace is not on one version. Run without --no-bump, or
    echo         stamp it by hand:
    echo           pnpm -r --include-workspace-root exec -- npm version ^<v^> --no-git-tag-version --allow-same-version
    exit /b 1
)
exit /b 0

REM ---------------------------------------------------------------------------
REM  %1 = pnpm filter (the package NAME, so the build runs where the script is).
:build
echo [INFO] Building %~1 ...
call "%PNPM%" -C "%ROOT%" --filter "%~1" run build
if errorlevel 1 ( echo [ERROR] build failed for %~1. & call :cleanup & exit /b 1 )
exit /b 0

REM ---------------------------------------------------------------------------
REM  %1 = directory. --if-present so a package with no tests is not an error;
REM  shio-react-sdk and shio-js-sdk have none, and that is a gap, not a failure.
:gate
echo [INFO] Publish gate: tests in %~1 ...
call "%PNPM%" -C "%ROOT%\%~1" run --if-present test
if errorlevel 1 ( echo [ERROR] tests failed in %~1. & call :cleanup & exit /b 1 )
exit /b 0

REM ---------------------------------------------------------------------------
REM  %1 = directory, %2 = package name (for the log line).
:publish
if "%DRY_RUN%"=="1" (
    echo [INFO] --dry-run: would publish %~2 from %~1
    exit /b 0
)
echo [INFO] Publishing %~2 ...
call "%PNPM%" -C "%ROOT%\%~1" publish --access public --no-git-checks
if errorlevel 1 ( echo [ERROR] pnpm publish failed for %~2. & call :cleanup & exit /b 1 )
exit /b 0

REM ---------------------------------------------------------------------------
:cleanup
if not "%NPMRC_TMP%"=="" if exist "%NPMRC_TMP%" del /q "%NPMRC_TMP%"
exit /b 0
