---
saves: add
because: files into a sibling
---

# Filing into a sibling project

Read this when a line you are about to write belongs to **another** roadkeep project — a
Turing defect met while working in Dumont — and that project is one `[projects]` declares
here. Every friction skill files this way, so none needs its own copy of the steps.

roadkeep never commits or pushes for you. It writes the line; the git commands below are
yours to run.

## Where the sibling is

`roadkeep config --table "projects.<name>"` says what this project declared: a `path`, a
`git` remote and its `ref`, or both. A `path` that exists wins.

## With a checkout (`path`)

1. `roadkeep -C <path> add --block <X> --symptom "…" --why "…"` — the line arrives with
   `(from: <this project>)`, which tells the sibling it is a report to re-analyse, not a
   design to build. Pass `--section` and `--section-body-file` as for any `add`.
2. Commit it **there** only if `git -C <path> status --short` lists nothing else: another
   session may be working in that tree, and its work must not ride under your title.
   Otherwise leave the line uncommitted and say so.

## With only the remote (`git`)

1. Clone shallow into a temporary directory: `git clone --depth 1 --branch <ref> <git> <tmp>`
   (the default branch where no `ref` is declared).
2. The same `add`, with `-C <tmp>`.
3. Commit on a branch named for the id and the filer: `git -C <tmp> switch -c <id>-from-<name>`,
   then `git -C <tmp> commit -am "docs(roadmap): file <id> from <name>"`.
4. Before pushing, take what moved since the clone and rebase with roadkeep's merge driver —
   a temporary clone has no config, and a host never runs a custom driver:
   `git -C <tmp> fetch --depth 50 origin <ref>`, then
   `git -C <tmp> -c merge.roadkeep.driver="roadkeep merge %O %A %B --path %P" rebase FETCH_HEAD`.
   If the id was spent meanwhile, `roadkeep -C <tmp> renumber <id> <next-id>` and rebase again.
5. Push the branch and open a request, **never** a push to `<ref>`: the review is the sibling
   re-analysing your report. `git -C <tmp> push -o merge_request.create origin <branch>` on
   GitLab, or `git push` then `gh pr create` on GitHub.

A collision that lands after your push is caught by the sibling's own CI `lint` as
`id.duplicate`.

## Then, here

The line that waits on it takes the dep: `roadkeep amend <id> --add-dep "<name>: <sibling-id>"`.
`deps`, `pick` and the next session start resolve it against the sibling's ledger, so nobody
relays the ship by hand.
