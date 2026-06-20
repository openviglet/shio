#!/usr/bin/env bash
#
# Runs a Maven analysis of the Shio project against the LOCAL SonarQube
# instance (NOT SonarCloud). Idempotent: safe to re-run.
#
# PREMISE: the SonarQube stack is ALREADY UP. Shio reuses the SonarQube
# Docker instance that lives in the Turing repo — there is no separate stack
# here. Start it once from the Turing repo if it is not running yet:
#
#   docker compose -f containers/sonarqube/docker-compose.yaml up -d   # (in the turing repo)
#   # wait until http://localhost:9100 is UP (first boot ~1-2 min)
#
# What this script does:
#   1. Ensures the admin password is set (admin/admin -> $SONAR_ADMIN_PASSWORD on
#      a fresh instance; if Turing already changed it, just uses the configured one).
#   2. (Re)generates an analysis token and caches it in scripts/sonar/.sonar-token.
#   3. Runs `mvn ... sonar:sonar` against http://localhost:9100 for project
#      key `viglet_shio` (kept distinct from Turing's `viglet_turing` and
#      Dumont's `viglet_dumont`).
#
# Env overrides:
#   SONAR_URL              (default http://localhost:9100)
#   SONAR_ADMIN_PASSWORD   (default TuringSonar@2026 -- matches the shared instance)
#   SONAR_GOAL             (default "verify" — runs the tests so JaCoCo reports
#                           coverage. Use "compile" to skip tests / report 0%.)
set -euo pipefail

SONAR_URL="${SONAR_URL:-http://localhost:9100}"
ADMIN_PW="${SONAR_ADMIN_PASSWORD:-TuringSonar@2026}"
GOAL="${SONAR_GOAL:-verify}"
PROJECT_KEY="viglet_shio"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TOKEN_FILE="$SCRIPT_DIR/.sonar-token"

echo "==> Checking SonarQube at $SONAR_URL (expected already up) ..."
for i in $(seq 1 30); do
  status="$(curl -fsS "$SONAR_URL/api/system/status" 2>/dev/null || true)"
  case "$status" in
    *'"status":"UP"'*) echo "    SonarQube is UP"; break ;;
    *) printf '.'; sleep 5 ;;
  esac
  if [ "$i" -eq 30 ]; then
    echo; echo "ERROR: SonarQube is not UP at $SONAR_URL." >&2
    echo "       Start it from the Turing repo:" >&2
    echo "         docker compose -f containers/sonarqube/docker-compose.yaml up -d" >&2
    exit 1
  fi
done
echo

# 1) Change default admin password if still admin/admin (ignore if already changed).
echo "==> Ensuring admin password is set ..."
if curl -fsS -u "admin:admin" "$SONAR_URL/api/authentication/validate" 2>/dev/null | grep -q '"valid":true'; then
  curl -fsS -u "admin:admin" -X POST "$SONAR_URL/api/users/change_password" \
    --data-urlencode "login=admin" \
    --data-urlencode "previousPassword=admin" \
    --data-urlencode "password=$ADMIN_PW" >/dev/null && echo "    Password changed."
else
  echo "    Default password already changed (using \$SONAR_ADMIN_PASSWORD)."
fi

# 2) (Re)generate an analysis token. Token value is shown only once, so revoke + regenerate.
echo "==> Generating analysis token ..."
curl -fsS -u "admin:$ADMIN_PW" -X POST "$SONAR_URL/api/user_tokens/revoke" \
  --data-urlencode "name=shio-local" >/dev/null 2>&1 || true
TOKEN="$(curl -fsS -u "admin:$ADMIN_PW" -X POST "$SONAR_URL/api/user_tokens/generate" \
  --data-urlencode "name=shio-local" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"
if [ -z "$TOKEN" ]; then echo "ERROR: could not generate token." >&2; exit 1; fi
printf '%s' "$TOKEN" > "$TOKEN_FILE"
echo "    Token cached in scripts/sonar/.sonar-token"

# 3) Run the analysis against the LOCAL instance from the multi-module reactor root.
#    Skip the Node/pnpm React frontend build (Java analysis only) to keep it fast
#    — these are the frontend-maven-plugin per-goal skip flags (same ones the
#    project guide uses for backend-only builds), so the Java build is untouched.
#    -Dmaven.test.failure.ignore=true: a coverage scan should still publish the
#    JaCoCo report (and Sonar analysis) even if a test fails.
#    Blank out sonar.organization (a SonarCloud-only concept).
echo "==> Running Maven analysis ($GOAL sonar:sonar) ..."
cd "$REPO_ROOT"
mvn $GOAL org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
  -Dskip.installnodepnpm=true \
  -Dskip.pnpm=true \
  -Dmaven.test.failure.ignore=true \
  -Dsonar.host.url="$SONAR_URL" \
  -Dsonar.token="$TOKEN" \
  -Dsonar.projectKey="$PROJECT_KEY" \
  -Dsonar.organization=

echo
echo "==> Done. Open $SONAR_URL/dashboard?id=$PROJECT_KEY"
echo "    List issues with: scripts/sonar/sonar-issues.sh"
