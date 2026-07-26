---
description: Apply a Shio blueprint — plan first, then apply, then prove it worked
argument-hint: "<blueprint> <site> [param=value ...]"
---

Apply a Shio blueprint: $ARGUMENTS

1. If the blueprint or the site is missing from the arguments, read `shio://blueprints`,
   list what is available with each package's parameters, and ask which one — do not guess.
2. Call `shio_apply` with `blueprint`, `site`, `params` and **`dryRun: true`**. Show the plan:
   the post-types it would create, every folder and page, and anything it reports as
   `exists` with fields missing — that last one means the instance already has a post-type of
   that name and the package's content expects fields it does not have.
3. Ask the user to confirm. If any line of the plan says `delete`, say so explicitly.
4. On confirmation, call it again without `dryRun`.
5. Report the `verify` block: whether every promised page exists and whether the content lint
   found errors. If the package carries front-end files, say that the server did **not** write
   them and that `shio apply --blueprint <name> --site <site>` from the terminal is what does.
6. The content is in DRAFT. Do not publish unless asked.
