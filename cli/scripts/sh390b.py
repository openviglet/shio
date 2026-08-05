import io

NL = "\n"

# --- 1. the two enumeration lists (SH384's tally: three per new helper NAME) ---------------------
p = "shio-app/src/test/java/com/viglet/shio/render/ShTemplateValidatorTest.java"
s = io.open(p, encoding="utf-8").read()
old = """                .containsExactly("file_url", "form", "getRelation", "menu", "navigation", "query", "search",
                        "translations");"""
assert s.count(old) == 1
s = s.replace(old, """                .containsExactly("file_url", "form", "getRelation", "image", "menu", "navigation",
                        "query", "search", "translations");""")
io.open(p, "w", encoding="utf-8", newline="").write(s)

p = "shio-app/src/test/java/com/viglet/shio/render/ShHelperSignatureLintTest.java"
s = io.open(p, encoding="utf-8").read()
old = """                .containsExactly("query", "navigation", "search", "getRelation", "file_url", "form", "menu",
                        "translations");"""
assert s.count(old) == 1
s = s.replace(old, """                .containsExactly("query", "navigation", "search", "getRelation", "file_url", "form",
                        "menu", "translations", "image");""")
io.open(p, "w", encoding="utf-8", newline="").write(s)

# --- 2. the field-lint fixture must CALL the new helper, or its negatives are silent about it ----
p = "shio-app/src/test/java/com/viglet/shio/api/agent/verify/ShTemplateFieldLintTest.java"
s = io.open(p, encoding="utf-8").read()
old = """              <img src="{{file_url HERO}}">"""
assert s.count(old) == 1
s = s.replace(old, """              <img src="{{file_url HERO}}">
              {{#image HERO sizes="50vw"}}<img src="{{url}}" width="{{width}}" height="{{height}}"
                srcset="{{srcset}}" sizes="{{sizes}}" alt="{{alt}}">{{/image}}""")
io.open(p, "w", encoding="utf-8", newline="").write(s)

# --- 3. the silent-catch population -------------------------------------------------------------
p = "shio-app/src/test/java/com/viglet/shio/ShSilentCatchInventoryTest.java"
s = io.open(p, encoding="utf-8").read()
old = '            "render", 4,'
assert s.count(old) == 1
new = """            // 6 since SH390, both in `{{#image}}` and both the same shape as `limit=`: a `WIDTH` that
            // is not a number means the same as an absent one (no ladder, plain url), and a signer
            // with no configured secret yields an empty `srcset` rather than a failed render — a page
            // that fell over because a transform could not be signed would take a site down for a
            // configuration mistake, and an image at its natural size is the correct degradation.
            // Neither is recorded: an unmeasurable image is not a degradation, and the signer's own
            // startup log already reports an enabled signer with no secret.
            "render", 6,"""
s = s.replace(old, new)

old = """                        The four that remain are deliberate and are named here so they are not \\
                        re-filed: a bad `limit=` falls back to the default, a bad `page=` falls back \\
                        to page one (SH384), the template validator collects the compile failure into \\
                        a report, and `ShRenderAPI` catches SH385's redirect to answer with a 302 — \\
                        that last one is control flow rather than a degradation, and it is counted \\
                        rather than exempted so the population stays a diff."""
assert s.count(old) == 1
new = """                        The six that remain are deliberate and are named here so they are not \\
                        re-filed: a bad `limit=` falls back to the default, a bad `page=` falls back \\
                        to page one (SH384), an unmeasurable image width and an unsignable transform \\
                        each fall back to a plain `<img>` (SH390), the template validator collects the \\
                        compile failure into a report, and `ShRenderAPI` catches SH385's redirect to \\
                        answer with a 302 — that last one is control flow rather than a degradation, \\
                        and it is counted rather than exempted so the population stays a diff."""
s = s.replace(old, new)
s = s.replace(".hasSize(4)\n                .allSatisfy(where -> assertThat(where)",
              ".hasSize(6)\n                .allSatisfy(where -> assertThat(where)")
io.open(p, "w", encoding="utf-8", newline="").write(s)
print("guards updated")
