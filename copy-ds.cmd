@echo off
setlocal

set DS_SRC=d:\Git\viglet\viglet-design-system
set DS_DEST=d:\Git\viglet\turing\2026.1\turing-react\node_modules\@openviglet\viglet-design-system

echo Building viglet-design-system...
pushd "%DS_SRC%"
call npm run build
if errorlevel 1 (
    echo BUILD FAILED
    popd
    exit /b 1
)
popd

echo Copying to turing-react node_modules...
if exist "%DS_DEST%" rmdir /s /q "%DS_DEST%"
mkdir "%DS_DEST%"

xcopy "%DS_SRC%\dist" "%DS_DEST%\dist\" /e /i /q
copy "%DS_SRC%\package.json" "%DS_DEST%\package.json" >nul

echo Done.
