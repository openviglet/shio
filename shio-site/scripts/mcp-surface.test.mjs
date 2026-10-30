// SH1341 — the site's MCP surface is the server's, and the landing counts it rather than spelling it.
//
// The README said twelve tools and the landing "Eleven tools, six resources", over a list of twelve
// tools, against a server registering thirteen resources. The landing now derives both numbers from
// `MCP_TOOLS` and `MCP_RESOURCES`, and this holds those two lists to the server's own sources, so the
// next tool moves every count at once.
//
// A source lint, like `site-claims.test.mjs`: the server's registration is read as Java text, which
// is where a tool or resource is declared, and no running instance is involved.
// @scans both reason: the tool and resource names are string literals in Java and TypeScript, and
// the landing's heading is markup, so neither a code-only nor a prose-only reader sees all three.
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const read = (path) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), "utf-8");
const SITE_CONTENT = read("../src/lib/site-content.ts");
const SURFACE = read("../src/components/sections/surface.tsx");
const MCP = new URL("../../shio-app/src/main/java/com/viglet/shio/api/agent/mcp/tools/", import.meta.url);

/** The quoted strings inside one `export const NAME = [ ... ];` of site-content.ts. */
function listed(name, pattern) {
  const body = SITE_CONTENT.match(new RegExp(`export const ${name} = \\[([\\s\\S]*?)\\n\\];`))?.[1] ?? "";
  return [...body.matchAll(pattern)].map((match) => match[1]).sort();
}

test("SH1341: the site lists exactly the tools the MCP server registers", () => {
  const server = [...new Set(readdirSync(MCP).filter((file) => file.endsWith("Tool.java"))
    .flatMap((file) => [...readFileSync(new URL(file, MCP), "utf-8").matchAll(/"(shio_[a-z_]+)"/g)].map((m) => m[1])))].sort();
  assert.ok(server.length >= 10, `SH1341: read only ${server.length} tool(s) from the server's sources`);
  assert.deepEqual(listed("MCP_TOOLS", /name: "(shio_[a-z_]+)"/g), server,
    "SH1341: MCP_TOOLS and the server's registered tools differ, so the landing's count is wrong");
});

test("SH1341: the site lists exactly the resources the MCP server registers", () => {
  const resources = readFileSync(new URL("ShMcpResources.java", MCP), "utf-8");
  // A templated resource is registered as its prefix ("shio://schema/"), the site writes the template.
  const server = [...new Set([...resources.matchAll(/"(shio:\/\/[a-z/]+)"/g)].map((m) => m[1]))].sort();
  const site = listed("MCP_RESOURCES", /"(shio:\/\/[^"]+)"/g).map((uri) => uri.replace(/\{[^}]+\}$/, "")).sort();
  assert.ok(server.length >= 10, `SH1341: read only ${server.length} resource(s) from the server's sources`);
  assert.deepEqual(site, server,
    "SH1341: MCP_RESOURCES and the server's registered resources differ, so the landing's count is wrong");
});

test("SH1341: the landing counts its lists instead of spelling a number", () => {
  assert.match(SURFACE, /counted\(MCP_TOOLS\.length/, "SH1341: the landing no longer derives the tool count");
  assert.match(SURFACE, /counted\(MCP_RESOURCES\.length/, "SH1341: the landing no longer derives the resource count");
  assert.doesNotMatch(SURFACE, /\b(Eleven|Twelve|Thirteen) tools\b/,
    "SH1341: a tool count is spelled in the landing again, which is how it went stale");
});
