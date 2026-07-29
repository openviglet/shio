#!/usr/bin/env python3
"""status-roadmap — count the active backlog in docs/ROADMAP.md by group and status.

The roadmap is the single home of status markers (agents.md, "Docs upkeep"), so it is
also the only place this can be measured. One task is one line:

    - 📋 **SH196** (deps: SH88 ✅) **No folder write is audited** — ... → §VIII.12

Markers counted:  📋 designed · 💭 idea · ⏳ partial · 🛠 in-progress.
The group is the nearest preceding `## ` heading, under its `# Part ` section.

Usage
  python scripts/status-roadmap.py              # table by group + totals
  python scripts/status-roadmap.py --tasks      # also list every task line
  python scripts/status-roadmap.py --status 📋  # only one status (repeatable)
  python scripts/status-roadmap.py --json       # machine-readable
  python scripts/status-roadmap.py --file <path>
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from pathlib import Path

# --- the four markers, in the roadmap legend's own order -------------------------
STATUSES = [
    ("📋", "DES", "Designed but not started"),
    ("💭", "IDEA", "Idea worth exploring; needs design"),
    ("⏳", "PART", "Partial — direction is right, more work remains"),
    ("🛠", "WIP", "In progress"),
]
MARKERS = [m for m, _, _ in STATUSES]
SHORT = {m: s for m, s, _ in STATUSES}
MEANING = {m: t for m, _, t in STATUSES}

# A task line: a list bullet (possibly nested), a marker, then the bold SH id.
# Blockquote lines (`> ⏳ **SH238 is half shipped**`) and the legend table rows
# (`| 📋 | ... |`) are not bullets, so they are excluded by construction.
TASK_RE = re.compile(
    r"^(?P<indent>[ \t]*)-[ \t]+(?P<marker>" + "|".join(MARKERS) + r")️?[ \t]+"
    r"\*\*(?P<id>SH[0-9]+[^*]*?)\*\*[ \t]*(?P<rest>.*)$"
)
BOLD_RE = re.compile(r"\*\*(.+?)\*\*")
FENCE_RE = re.compile(r"^\s*(```|~~~)")
PART_RE = re.compile(r"^#[ \t]+(.*\S)")
GROUP_RE = re.compile(r"^##[ \t]+(.*\S)")

# --- the audit's shapes: every other way a marker reaches a line ----------------
# A shipped/superseded bullet (`- ✅ **SH22** …`) and a shipped pointer
# (`> ✅ **SH191 has shipped** → …`) must never be counted; a bullet that carries
# an active marker but no bold SH id would be dropped *silently*, which is the one
# failure this script could have and not show. --audit names all three.
SHIPPED_MARKERS = ["✅", "⛔"]
SHIPPED_BULLET_RE = re.compile(r"^[ \t]*-[ \t]+(" + "|".join(SHIPPED_MARKERS) + r")️?[ \t]")
POINTER_RE = re.compile(
    r"^[ \t]*>[ \t]*(" + "|".join(SHIPPED_MARKERS + MARKERS) + r")️?[ \t]"
)
LEGEND_RE = re.compile(r"^[ \t]*\|[ \t]*(" + "|".join(MARKERS) + r")️?[ \t]*\|")
SUSPECT_RE = re.compile(r"^[ \t]*-[ \t]+(" + "|".join(MARKERS) + r")️?[ \t]")

DEFAULT_ROADMAP = Path(__file__).resolve().parent.parent / "docs" / "ROADMAP.md"


def parse(path: Path, audit: list[dict] | None = None) -> list[dict]:
    """Return one dict per task line, in document order.

    When `audit` is given, append every marker-bearing line that was *not* counted,
    tagged with why — so "is it counting shipped tasks?" is answerable by the tool.
    """
    tasks: list[dict] = []
    part = ""
    group = "(no group)"
    in_fence = False

    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if FENCE_RE.match(raw):
            in_fence = not in_fence
            continue
        if in_fence:
            continue

        if m := PART_RE.match(raw):
            part, group = m.group(1), "(no group)"
            continue
        if m := GROUP_RE.match(raw):
            group = m.group(1)
            continue

        if m := TASK_RE.match(raw):
            bolds = BOLD_RE.findall(m.group("rest"))
            tasks.append(
                {
                    "id": m.group("id").strip(),
                    "status": m.group("marker"),
                    "title": bolds[0].strip() if bolds else "",
                    "group": group,
                    "part": part,
                    "line": lineno,
                    "nested": bool(m.group("indent")),
                }
            )
            continue

        if audit is None:
            continue
        if SHIPPED_BULLET_RE.match(raw):
            why = "shipped/superseded bullet — excluded"
        elif POINTER_RE.match(raw):
            why = "blockquote pointer — excluded"
        elif LEGEND_RE.match(raw):
            why = "legend table row — excluded"
        elif SUSPECT_RE.match(raw):
            why = "LOOKS LIKE A TASK BUT HAS NO **SH<n>** — NOT COUNTED"
        else:
            continue
        audit.append({"line": lineno, "group": group, "why": why, "text": raw.strip()})
    return tasks


def short_group(name: str) -> str:
    """`Block H — The Agent Gateway (MCP ...)` → `Block H — The Agent Gateway`."""
    head = name.split(" (")[0]
    return head if len(head) <= 46 else head[:45] + "…"


def render(tasks: list[dict], show_tasks: bool, name: str = "ROADMAP.md") -> str:
    groups: dict[str, Counter] = {}
    order: list[str] = []
    part_of: dict[str, str] = {}
    for t in tasks:
        if t["group"] not in groups:
            groups[t["group"]] = Counter()
            order.append(t["group"])
            part_of[t["group"]] = t["part"]
        groups[t["group"]][t["status"]] += 1

    label_w = max([len("Group")] + [len(short_group(g)) for g in order]) if order else 5
    cols = [SHORT[m] for m in MARKERS]
    out: list[str] = []

    header = f"{'Group'.ljust(label_w)} │ " + " ".join(c.rjust(4) for c in cols) + " │ TOTAL"
    rule = f"{'─' * label_w}─┼─" + "─" * (5 * len(cols) - 1) + "─┼──────"

    out.append(f"Roadmap status — {len(tasks)} active task lines")
    if not order:
        out.append("  (no task line matches)")
        return "\n".join(out)
    out.append("")
    out.append(header)
    out.append(rule)

    current_part = None
    for g in order:
        if part_of[g] != current_part:
            current_part = part_of[g]
            if current_part:
                out.append(f"{current_part}")
        c = groups[g]
        cells = " ".join((str(c[m]) if c[m] else "·").rjust(4) for m in MARKERS)
        out.append(f"{short_group(g).ljust(label_w)} │ {cells} │ {sum(c.values()):5d}")

    total = Counter(t["status"] for t in tasks)
    out.append(rule)
    cells = " ".join(str(total[m]).rjust(4) for m in MARKERS)
    out.append(f"{'TOTAL'.ljust(label_w)} │ {cells} │ {len(tasks):5d}")
    out.append("")
    for m, s, meaning in STATUSES:
        out.append(f"  {s:<5} {m}  {total[m]:>3}  {meaning}")

    if show_tasks:
        out.append("")
        current = None
        for t in tasks:
            if t["group"] != current:
                current = t["group"]
                out.append("")
                out.append(f"── {current}")
            out.append(
                f"  {t['status']} {SHORT[t['status']]:<5} {t['id']:<12} {t['title']}"
                f"  ({name}:{t['line']})"
            )

    return "\n".join(out)


def render_audit(skipped: list[dict], name: str) -> str:
    """Summarise, then list, every marker-bearing line that was not counted."""
    out = ["", "Audit — marker-bearing lines NOT counted"]
    by_reason: dict[str, list[dict]] = {}
    for s in skipped:
        by_reason.setdefault(s["why"], []).append(s)

    for why, rows in by_reason.items():
        out.append(f"  {len(rows):>4}  {why}")

    suspects = [s for s in skipped if s["why"].startswith("LOOKS LIKE")]
    if suspects:
        out.append("")
        out.append("  These would be invisible to the count — fix the line or the parser:")
        for s in suspects:
            out.append(f"    {name}:{s['line']}  {s['text'][:110]}")
    else:
        out.append("")
        out.append("  No task-shaped line was dropped: every bullet with an active marker")
        out.append("  carries a **SH<n>** id and is counted above.")
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="status-roadmap",
        description="Count docs/ROADMAP.md's active backlog by group and status marker.",
    )
    ap.add_argument("--file", type=Path, default=DEFAULT_ROADMAP, help="roadmap path")
    ap.add_argument("--tasks", action="store_true", help="list every task line too")
    ap.add_argument(
        "--audit",
        action="store_true",
        help="show every marker-bearing line that was NOT counted, and why",
    )
    ap.add_argument("--json", action="store_true", dest="as_json", help="machine-readable output")
    ap.add_argument(
        "--status",
        action="append",
        default=[],
        metavar="MARKER",
        help="keep only these statuses — an emoji (📋) or its short code (DES/IDEA/PART/WIP)",
    )
    args = ap.parse_args(argv)

    # Emoji on a Windows console needs UTF-8; the default locale codec raises.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8", errors="replace")
        except (AttributeError, OSError):
            pass

    if not args.file.is_file():
        print(f"status-roadmap: not a file: {args.file}", file=sys.stderr)
        return 2

    wanted: set[str] = set()
    for raw in args.status:
        key = raw.strip().rstrip("️")
        if key in MEANING:
            wanted.add(key)
            continue
        by_code = {s.upper(): m for m, s, _ in STATUSES}
        if key.upper() in by_code:
            wanted.add(by_code[key.upper()])
            continue
        print(
            f"status-roadmap: unknown status {raw!r}; "
            f"allowed: {', '.join(f'{m} ({s})' for m, s, _ in STATUSES)}",
            file=sys.stderr,
        )
        return 2

    skipped: list[dict] = []
    tasks = parse(args.file, audit=skipped if args.audit else None)
    if wanted:
        tasks = [t for t in tasks if t["status"] in wanted]

    if args.as_json:
        groups: dict[str, dict] = {}
        for t in tasks:
            g = groups.setdefault(
                t["group"],
                {"part": t["part"], "counts": {m: 0 for m in MARKERS}, "total": 0},
            )
            g["counts"][t["status"]] += 1
            g["total"] += 1
        total = Counter(t["status"] for t in tasks)
        print(
            json.dumps(
                {
                    "file": str(args.file),
                    "total": len(tasks),
                    "legend": {m: MEANING[m] for m in MARKERS},
                    "counts": {m: total[m] for m in MARKERS},
                    "groups": groups,
                    "tasks": tasks,
                    **({"notCounted": skipped} if args.audit else {}),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(render(tasks, args.tasks, args.file.name))
        if args.audit:
            print(render_audit(skipped, args.file.name))
    return 0


if __name__ == "__main__":
    sys.exit(main())
