@echo off
setlocal

REM ============================================================================
REM update-roadkeep.cmd - Update the roadkeep Claude Code plugin to the latest
REM version published on its marketplace (GitHub: alegauss/roadkeep).
REM
REM roadkeep owns docs/ROADMAP.md, docs/CHANGELOG.md and docs/IMPROVEMENTS.md in
REM this repo (see roadkeep.toml and .claude/settings.json), so its CLI, its MCP
REM tools and its skill all move together with the plugin version. Updating it is
REM two steps, and doing only the second is the usual mistake:
REM
REM   1. `claude plugin marketplace update alegauss` re-fetches the marketplace
REM      manifest from GitHub. Without this, step 2 compares against whatever
REM      version was cached locally and reports "already up to date" against a
REM      stale catalogue.
REM   2. `claude plugin update roadkeep@alegauss --scope project` installs it.
REM      The scope matters: this plugin is enabled in .claude/settings.json
REM      (project scope), not in the user settings, and `claude plugin update`
REM      defaults to --scope user - which would fail to find it.
REM
REM The installed version is read before and after from
REM   %%CLAUDE_CONFIG_DIR%%\plugins\installed_plugins.json
REM so the run says what actually changed rather than only that it succeeded.
REM
REM Usage:
REM   update-roadkeep.cmd              Refresh the marketplace, then update.
REM   update-roadkeep.cmd --check      Only report the installed version.
REM
REM Note: a plugin update takes effect on the NEXT Claude Code session. The
REM running session keeps the version it started with.
REM ============================================================================

set "PLUGIN=roadkeep@alegauss"
set "MARKETPLACE=alegauss"

set "CHECK_ONLY=0"
if /i "%~1"=="--check" set "CHECK_ONLY=1"
if not "%~1"=="" if not "%CHECK_ONLY%"=="1" (
    echo [ERROR] Unknown argument: %~1
    echo         Usage: update-roadkeep.cmd [--check]
    exit /b 1
)

REM --- Where the plugin cache lives. CLAUDE_CONFIG_DIR is set on this machine --
REM     to %USERPROFILE%\.claude-pessoal; ~\.claude is the default and is a stale
REM     decoy here, so never hard-code either one.
if defined CLAUDE_CONFIG_DIR (
    set "CFG=%CLAUDE_CONFIG_DIR%"
) else (
    set "CFG=%USERPROFILE%\.claude"
)
set "PLUGINS_JSON=%CFG%\plugins\installed_plugins.json"

where claude >nul 2>nul
if errorlevel 1 (
    echo [ERROR] `claude` is not on PATH. Install the Claude Code CLI first.
    exit /b 1
)

echo ============================================================
echo  Updating the roadkeep Claude Code plugin
echo  plugin      : %PLUGIN%
echo  marketplace : %MARKETPLACE% ^(github alegauss/roadkeep^)
echo  config dir  : %CFG%
echo ============================================================

call :read_version BEFORE
echo [INFO] Installed version: %BEFORE%

if "%CHECK_ONLY%"=="1" (
    endlocal
    exit /b 0
)

echo.
echo [INFO] Refreshing the %MARKETPLACE% marketplace from GitHub ...
call claude plugin marketplace update %MARKETPLACE%
if errorlevel 1 (
    echo [ERROR] marketplace update failed. Check network access to github.com,
    echo         and that the marketplace is configured:
    echo           claude plugin marketplace list
    exit /b 1
)

echo.
echo [INFO] Updating %PLUGIN% ^(project scope^) ...
call claude plugin update %PLUGIN% --scope project
if errorlevel 1 (
    echo [ERROR] plugin update failed. If it reports the plugin is not installed,
    echo         confirm the scope with:
    echo           claude plugin list
    exit /b 1
)

call :read_version AFTER
echo.
if "%BEFORE%"=="%AFTER%" (
    echo [OK] roadkeep is at %AFTER% - already the latest published version.
) else (
    echo [OK] roadkeep updated: %BEFORE% -^> %AFTER%
    echo      Restart Claude Code for the new version to load - the running
    echo      session keeps the CLI, MCP tools and skill it started with.
)
endlocal
exit /b 0

REM ---------------------------------------------------------------------------
REM  Read the installed version out of installed_plugins.json into the variable
REM  named by %1. Reports `unknown` rather than failing: a missing or malformed
REM  file must not stop an update, it only makes the before/after line vaguer.
:read_version
set "%~1=unknown"
if not exist "%PLUGINS_JSON%" exit /b 0
where node >nul 2>nul
if errorlevel 1 exit /b 0
for /f "usebackq delims=" %%v in (`node -e "var fs=require('fs');try{var j=JSON.parse(fs.readFileSync(process.argv[1],'utf8'));var e=(j.plugins||{})['roadkeep@alegauss']||[];var v=e.map(function(x){return x.version;});console.log(v.length?v.join(','):'not installed');}catch(err){console.log('unknown');}" "%PLUGINS_JSON%"`) do set "%~1=%%v"
exit /b 0
