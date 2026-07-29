@echo off
rem status-roadmap - active-backlog counts by group and status marker.
rem Flags: --tasks --json --status <DES|IDEA|PART|WIP> --file <path>
rem Implementation: scripts\status-roadmap.py
setlocal
set "PYTHONUTF8=1"
python "%~dp0scripts\status-roadmap.py" %*
endlocal
