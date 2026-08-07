---
description: Read the Shio instance — what it can do, then its model, sitemap and conventions
argument-hint: "[site]"
---

Learn this Shio instance before doing anything to it.

1. Read the `shio://manifest` resource **whole**. Report the `features` that are `false` and
   the `limits`, because those are the two things that differ between instances and the two
   an assumption gets wrong. Read `startHere` and follow it if it disagrees with this file —
   it comes from the instance and this file does not.

   **Do this first, always, and do not skip it because the task seems narrow.** A capability
   check costs one call and the alternative costs a session: a documented run once diagnosed
   a 500 by grepping the server's log file while `diagnostics: true` sat in this list, and
   twice proposed building something the same list already flagged shipped. Before concluding
   Shio cannot do a thing, look for it here.

2. Call `shio_context`$ARGUMENTS. Report: the sites, the post-types with their fields, the
   shape of the folder tree, and any conventions the instance states. Keep the `digest`
   value in your reply so a later session can compare it instead of re-reading.

3. Call `shio_remember` with no arguments. Whoever worked here last may have written down
   what this instance is for, which sites are fixtures somebody wants kept, and why something
   is the way it is. It is cheaper than re-deriving and it is the only place a fact survives
   a session.

4. Read the `shio://blueprints` resource and list what is available with the parameters each
   package takes.

5. Call `shio_verify` once, so the starting state is known rather than assumed.

Then stop and summarise. Do not write anything yet — say what you would do next and what it
would cost.

**Before you finish any later work here, write back what the next session should not have to
re-derive** (`shio_remember <key> "<note>"`). A decision nobody recorded is a decision
somebody repeats.
