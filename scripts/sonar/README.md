# Local SonarQube analysis for Shio

Shio reuses the **SonarQube Docker stack that lives in the Turing repo** — there
is no separate stack here (same arrangement as Dumont). These scripts run a local
code analysis of Shio against that instance (`http://localhost:9100`), under the
project key `viglet_shio` (kept distinct from Turing's `viglet_turing` and
Dumont's `viglet_dumont`).

## Premise: the SonarQube instance is already up

These scripts assume SonarQube is already running. If it is not, start it once
from the **Turing** repo:

```bash
# in the turing repo
docker compose -f containers/sonarqube/docker-compose.yaml up -d
# wait until http://localhost:9100 is UP (first boot ~1-2 min)
```

Default login is `admin` / `TuringSonar@2026` (the scripts handle the first-boot
`admin/admin` -> password change automatically and reuse the configured one
afterwards).

## Usage

```bash
# 1. Bootstrap a token + run the Maven analysis (from the Shio repo root)
scripts/sonar/sonar-scan.sh

# 2. List open issues via the Web API
scripts/sonar/sonar-issues.sh
```

`sonar-scan.sh` runs from the multi-module reactor root, so the backend
(`shio-app`) is analyzed in one pass. The Node/pnpm React frontend build is
skipped for speed via the `frontend-maven-plugin` skip flags
(`-Dskip.installnodepnpm=true -Dskip.pnpm=true`), so the Java build is untouched.

### `sonar-scan.sh` env overrides

| Var | Default | Purpose |
|-----|---------|---------|
| `SONAR_URL` | `http://localhost:9100` | SonarQube base URL |
| `SONAR_ADMIN_PASSWORD` | `TuringSonar@2026` | Admin password (matches the shared instance) |
| `SONAR_GOAL` | `verify` | Runs tests for coverage; use `compile` to skip tests / report 0% |

### `sonar-issues.sh` views

```bash
# Breakdown by rule (default), worst-first
scripts/sonar/sonar-issues.sh

# Only the worst severities
SONAR_SEVERITIES=BLOCKER,CRITICAL scripts/sonar/sonar-issues.sh

# Occurrences of a single rule (file:line list)
SONAR_RULE=java:S1118 scripts/sonar/sonar-issues.sh

# Full per-issue dump
SONAR_LIST=1 scripts/sonar/sonar-issues.sh
```

The analysis token is cached in `scripts/sonar/.sonar-token` (git-ignored).

## Notes

- This is **separate** from the app stack on purpose — analysis doesn't need the
  app running.
- The project's `pom.xml` keeps SonarCloud as the default target; the local scan
  overrides `sonar.host.url`/`sonar.token` and blanks `sonar.organization` (a
  SonarCloud-only concept) on the command line, so nothing in the POM changes.
