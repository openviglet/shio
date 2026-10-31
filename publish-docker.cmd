@echo off
setlocal enabledelayedexpansion

REM ============================================================================
REM publish-docker.cmd — Build the Viglet Shio CMS Docker image LOCALLY and
REM push it to the GitHub Container Registry (ghcr.io/openviglet/shio).
REM
REM Unlike .github/workflows/publish-docker.yml (which clones + builds the source
REM INSIDE the container), this builds the Spring Boot fat-jar on the HOST using
REM your authenticated ~/.m2 settings, then packages it with Dockerfile.local.
REM Why: viglet-core is a PRIVATE GitHub Packages dependency — an in-container
REM Maven build has no credentials and fails with 401. Building on the host also
REM guarantees the image contains your local working-tree changes, not just the
REM last pushed commit.
REM
REM Auth (push to ghcr.io): set a GitHub PAT with `write:packages` scope:
REM   set CR_PAT=ghp_xxxxxxxxxxxxxxxxxxxx
REM   set CR_USER=your-github-username        (defaults to "openviglet")
REM If CR_PAT is unset, the script assumes you are already `docker login`-ed.
REM
REM Usage:
REM   publish-docker.cmd            Build the jar on the host, then image + push.
REM   publish-docker.cmd --nobuild  Skip the Maven build; reuse the existing jar
REM                                 at shio-app/target/viglet-shio.jar.
REM ============================================================================

set "REGISTRY=ghcr.io"
set "IMAGE_NAME=openviglet/shio"
set "IMAGE=%REGISTRY%/%IMAGE_NAME%"
set "JAR=shio-app\target\viglet-shio.jar"

set "SKIP_BUILD=0"
if /i "%~1"=="--nobuild" set "SKIP_BUILD=1"
if /i "%~1"=="-n"        set "SKIP_BUILD=1"

REM --- Resolve project version from the root pom.xml -------------------------
REM Shio's <version> is the ${revision} property, so dereference it when needed.
for /f "delims=" %%v in ('powershell -NoProfile -Command "$x=(Select-Xml -Path pom.xml -XPath '/*[local-name()=\"project\"]/*[local-name()=\"version\"]').Node.InnerText; if($x -match '\$\{(.+)\}'){ $p=$matches[1]; $x=(Select-Xml -Path pom.xml -XPath ('/*[local-name()=\"project\"]/*[local-name()=\"properties\"]/*[local-name()=\"'+$p+'\"]')).Node.InnerText }; $x"') do set "VERSION=%%v"
if "%VERSION%"=="" (
    echo [ERROR] Could not resolve project version from pom.xml.
    exit /b 1
)

REM --- Resolve current commit SHA for the image revision label ---------------
for /f "delims=" %%s in ('git rev-parse HEAD') do set "GIT_SHA=%%s"
set "SHA_SHORT=%GIT_SHA:~0,12%"

echo ============================================================
echo  Image   : %IMAGE%
echo  Version : %VERSION%
echo  Commit  : %GIT_SHA%
echo  Build   : %SKIP_BUILD%==0 ^(host mvn build^) / 1 ^(reuse jar^)
echo ============================================================

REM --- Build the fat-jar on the host (authenticated ~/.m2) -------------------
if "%SKIP_BUILD%"=="0" (
    echo [INFO] Building viglet-shio.jar on the host ...
    call mvnw.cmd clean package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Maven build failed.
        exit /b 1
    )
) else (
    echo [INFO] --nobuild: reusing existing jar.
)

if not exist "%JAR%" (
    echo [ERROR] %JAR% not found. Run without --nobuild, or build it first.
    exit /b 1
)

REM --- Log in to ghcr.io if a PAT is provided --------------------------------
if not "%CR_PAT%"=="" (
    if "%CR_USER%"=="" set "CR_USER=openviglet"
    echo [INFO] Logging in to %REGISTRY% as !CR_USER! ...
    echo !CR_PAT! | docker login %REGISTRY% -u !CR_USER! --password-stdin
    if errorlevel 1 (
        echo [ERROR] docker login failed.
        exit /b 1
    )
) else (
    echo [INFO] CR_PAT not set — assuming an existing docker login to %REGISTRY%.
)

REM --- Build the runtime image (multi-tag: latest, version, sha) and push ----
docker buildx build ^
    --file Dockerfile.local ^
    --tag %IMAGE%:latest ^
    --tag %IMAGE%:%VERSION% ^
    --tag %IMAGE%:sha-%SHA_SHORT% ^
    --build-arg CACHE_BUST=%GIT_SHA% ^
    --push ^
    .

if errorlevel 1 (
    echo [ERROR] Docker build/push failed.
    exit /b 1
)

echo.
echo [OK] Pushed:
echo      %IMAGE%:latest
echo      %IMAGE%:%VERSION%
echo      %IMAGE%:sha-%SHA_SHORT%
endlocal
