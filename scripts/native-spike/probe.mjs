#!/usr/bin/env node
// SH1249 — the paths the timing replay does not reach and the native image is most likely to
// break: a page rendered through Handlebars, image transforms through ImageIO (JPEG, PNG and
// WebP, the last one a JNI library), and GraphQL on the CDA. Run against a JVM and a native
// container on the same fresh store; a path is "works" only when both answer the same status
// and a body of the same kind.
//
//   node scripts/native-spike/probe.mjs <image> [--docker-arg <arg> ...]

import { execFileSync } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";

import { ConsoleSession, basic } from "../../cli/conformance/support/instance.mjs";

const PORT = 2797;
const BASE = `http://localhost:${PORT}`;
const USER = "admin";
const PASSWORD = "spike-sh1249";
const auth = { Authorization: basic(USER, PASSWORD) };

const [image, ...rest] = process.argv.slice(2);
const dockerArgs = [];
for (let i = 0; i < rest.length; i++) if (rest[i] === "--docker-arg") dockerArgs.push(rest[++i]);

const docker = (...args) => execFileSync("docker", args, { encoding: "utf8" }).trim();
const name = `shio-probe-${process.pid}`;
docker("run", "-d", "--rm", "--name", name, "-p", `${PORT}:2710`,
  "-e", `SHIO_ADMIN_PASSWORD=${PASSWORD}`, ...dockerArgs, image);

async function get(path, headers = auth) {
  const res = await fetch(`${BASE}${path}`, { headers, signal: AbortSignal.timeout(60_000) });
  const body = Buffer.from(await res.arrayBuffer());
  return { status: res.status, type: res.headers.get("content-type"), bytes: body.length, body };
}

try {
  for (let i = 0; ; i++) {
    try {
      if ((await get("/api/v2/csrf")).status === 200) break;
    } catch {
      // not listening yet
    }
    if (i > 1200) throw new Error("never came up");
    await sleep(100);
  }
  const out = {};
  const show = (key, r) => (out[key] = { status: r.status, type: r.type, bytes: r.bytes });

  show("page /sites/viglet/default/en-us", await get("/sites/viglet/default/en-us"));

  const listing = JSON.parse((await get("/api/v2/staticfile?size=200")).body.toString("utf8"));
  const picture = listing.items.find((f) => /^image\/(jpeg|png)$/.test(f.contentType));
  out.image = picture ? `${picture.fileName} (${picture.contentType})` : "none in the sample site";
  if (picture) {
    for (const format of ["jpeg", "png", "webp"]) {
      show(`transform w=120 ${format}`, await get(`/api/v2/staticfile/${picture.id}?w=120&format=${format}`));
    }
  }

  const session = new ConsoleSession({ baseUrl: BASE, user: USER, password: PASSWORD });
  const token = await session.createToken("SH1249 probe", "READ");
  const graphql = await fetch(`${BASE}/graphql`, {
    method: "POST",
    headers: { Key: token.token, "Content-Type": "application/json" },
    body: JSON.stringify({ query: "{ __schema { queryType { name } } }" }),
  });
  out["cda graphql introspection"] = { status: graphql.status, body: (await graphql.text()).slice(0, 80) };

  console.log(JSON.stringify({ image, ...out }, null, 2));
} finally {
  docker("rm", "-f", name);
}
