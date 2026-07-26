---
description: Verify Shio content and report honestly — lint, routes, page structure, incidents
argument-hint: "[site]"
---

Verify the Shio content$ARGUMENTS and report what is actually true.

1. `shio_verify` — the content lint and route resolution. Branch on `ok`: false means at
   least one **error**. Warnings are reported, not treated as failures.
2. If `found` is larger than the number of findings returned, say so and re-run with a higher
   `limit` before drawing conclusions about what is not in the list.
3. For any page the user cares about, `shio_digest` on its address — confirm the heading
   outline and that every image has alt text.
4. `shio_changes` — what changed recently, and by whom. A human edit in the console is the
   usual explanation for a surprise.
5. Read `GET /api/v2/agent/diagnostics` for asynchronous failures. Remember the window is in
   memory and bounded: an empty report means "nothing since it started collecting".

Finish with a short report that separates **errors** (broken now), **warnings** (probably
wrong) and **not checked** (and why). Do not fix anything without asking.
