#!/usr/bin/env bash
#
# Lists open issues for the Shio project from the LOCAL SonarQube instance
# (the SonarQube Docker stack that lives in the Turing repo, on :9100).
#
# Default view: a breakdown by RULE (count + severity + message), worst-first —
# the right granularity to fix "one rule at a time".
#
# Drill into a single rule's occurrences (file:line list):
#   SONAR_RULE=java:S1118 scripts/sonar/sonar-issues.sh
#
# Full per-issue dump (file:line  [rule]  SEVERITY  message):
#   SONAR_LIST=1 scripts/sonar/sonar-issues.sh
#
# Uses the token cached by sonar-scan.sh (scripts/sonar/.sonar-token).
#
# Env overrides:
#   SONAR_URL          (default http://localhost:9100)
#   SONAR_PROJECT_KEY  (default viglet_shio)
#   SONAR_TYPES        (default BUG,VULNERABILITY,CODE_SMELL) -- comma list, or empty for all
#   SONAR_SEVERITIES   (e.g. BLOCKER,CRITICAL,MAJOR) -- optional filter
#   SONAR_RULE         (e.g. java:S1118) -- show occurrences of one rule
#   SONAR_LIST         (1) -- full per-issue dump instead of the rule breakdown
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOKEN_FILE="$SCRIPT_DIR/.sonar-token"
[ -f "$TOKEN_FILE" ] || { echo "No token found. Run scripts/sonar/sonar-scan.sh first." >&2; exit 1; }

SONAR_URL="${SONAR_URL:-http://localhost:9100}" \
PROJECT_KEY="${SONAR_PROJECT_KEY:-viglet_shio}" \
TYPES="${SONAR_TYPES:-BUG,VULNERABILITY,CODE_SMELL}" \
SEVERITIES="${SONAR_SEVERITIES:-}" \
RULE="${SONAR_RULE:-}" \
LIST="${SONAR_LIST:-}" \
TOKEN="$(cat "$TOKEN_FILE")" \
python - <<'PY'
import os, json, base64, urllib.request, urllib.parse, collections

url    = os.environ["SONAR_URL"].rstrip("/")
token  = os.environ["TOKEN"]
params = {"componentKeys": os.environ["PROJECT_KEY"], "resolved": "false", "ps": "500"}
if os.environ.get("TYPES"):      params["types"] = os.environ["TYPES"]
if os.environ.get("SEVERITIES"): params["severities"] = os.environ["SEVERITIES"]
if os.environ.get("RULE"):       params["rules"] = os.environ["RULE"]

auth = base64.b64encode((token + ":").encode()).decode()

def fetch(page):
    q = urllib.parse.urlencode({**params, "p": page})
    req = urllib.request.Request(f"{url}/api/issues/search?{q}",
                                 headers={"Authorization": "Basic " + auth})
    with urllib.request.urlopen(req) as r:
        return json.load(r)

first = fetch(1)
total = first.get("total", 0)
issues = list(first.get("issues", []))
# SonarQube caps paging at 10000; fetch remaining pages.
pages = min((total + 499) // 500, 20)
for p in range(2, pages + 1):
    issues.extend(fetch(p).get("issues", []))

def comp(it):  return it.get("component", "").split(":")[-1]

types = os.environ.get("TYPES") or "ALL"
print(f"==> {total} open issue(s) for {os.environ['PROJECT_KEY']} (types={types})")
if total > len(issues):
    print(f"    (showing first {len(issues)}; SonarQube caps the issues API at 10000)")
print()

if os.environ.get("LIST") or os.environ.get("RULE"):
    for it in sorted(issues, key=lambda i: (comp(i), i.get("line", 0) or 0)):
        print(f"{comp(it)}:{it.get('line','-')}\t[{it.get('rule','')}]\t{it.get('severity','')}\t{it.get('message','').replace(chr(10),' ')}")
else:
    by_rule = collections.Counter(it.get("rule", "") for it in issues)
    sev_of  = {it.get("rule",""): it.get("severity","") for it in issues}
    msg_of  = {it.get("rule",""): it.get("message","") for it in issues}
    print(f"{'COUNT':>6}  {'SEVERITY':<9} {'RULE':<16} EXAMPLE MESSAGE")
    for rule, n in by_rule.most_common():
        print(f"{n:>6}  {sev_of[rule]:<9} {rule:<16} {msg_of[rule][:80]}")
    print()
    print("Drill into one rule:  SONAR_RULE=<rule> scripts/sonar/sonar-issues.sh")
PY
