---
title: Webhooks
description: "Outbound webhooks in Viglet Shio: subscribing per site and per event, the signed JSON payload, verifying the HMAC-SHA256 signature, and exactly what delivery does and does not guarantee."
sidebar:
  label: Webhooks
---

When content is **published**, **unpublished** or **deleted**, Shio can POST a small
signed JSON body to a URL you choose. The usual reason is to rebuild or revalidate a
static front end at the moment its content changed, rather than on a timer.

---

## Subscribing

Create a subscription in the console under **Administration → Webhooks**, or through the
API at `/api/v2/site-webhook`.

| Field | Meaning |
|---|---|
| **Name** | Yours, for the list. e.g. *Static Site Regenerator*. |
| **Target URL** | Where the signed POST goes. e.g. `https://example.com/api/revalidate`. |
| **Site** | One site, or **All sites** for a catch-all subscription. |
| **Events** | **All events**, or any subset of publish / unpublish / delete. |
| **Shared secret** | The HMAC-SHA256 key. **Auto-generated if you leave it blank on creation.** |
| **Enabled** | Turn off to pause delivery without deleting the subscription. |

Subscriptions are independent: two of them pointed at the same URL both fire, and a
catch-all plus a site-scoped subscription both matching one event both fire.

:::caution
**A subscription with no secret delivers nothing.** Signing is not optional: a target
with a blank secret is skipped, with a warning in the server log. Leave the field blank
on creation and Shio generates one for you; do not clear it afterwards.
:::

The secret is **write-only on the wire**: once saved, reading the subscription back
through the API does not return it. Copy it when you create it.

---

## The payload

`POST` to your target URL, `Content-Type: application/json`:

```json
{
  "event": "publish",
  "siteId": "6e4bd8b3-1b3a-4f9d-9d67-52c1e7f9ab10",
  "postId": "0b7f2c41-9a8e-4a1f-bd0c-3d9a5f1e2c77",
  "url": "/blog/hello-world",
  "occurredAt": "2026-08-11T13:07:42.512Z"
}
```

| Field | Notes |
|---|---|
| `event` | `publish`, `unpublish` or `delete`. Always present. |
| `siteId` | The post's site. Present, but may be `null` if it could not be resolved. |
| `postId` | The post. Always present. |
| `url` | The post's friendly URL, normalised to a leading slash. **Omitted** when the post has none. |
| `occurredAt` | ISO-8601 instant of the transition. **Omitted** if unknown. |

Two things the payload deliberately does **not** carry: the post's **content**, and the
post's **locale**. It is a notification, not a delivery mechanism: fetch what you need
through the [Content Delivery API](/shio/docs/headless/content-delivery-api/) using `postId`, or
regenerate the route at `url`.

For a `delete` there is nothing left to fetch, which is why `url` is worth acting on
directly.

---

## Verifying the signature

Every request carries:

```http
x-shio-signature: sha256=<lowercase hex>
```

The value is `HMAC-SHA256(secret, rawRequestBody)`, hex-encoded in lowercase, prefixed
with `sha256=`.

**Verify it over the raw bytes of the body, before parsing.** Re-serialising the parsed
JSON produces different bytes and the digest will not match.

**Compare in constant time.** A plain string comparison leaks the digest one byte at a
time.

### Node / Next.js

```js
import crypto from "node:crypto";

export async function POST(request) {
  const raw = Buffer.from(await request.arrayBuffer());          // raw bytes, not JSON
  const expected =
    "sha256=" + crypto.createHmac("sha256", process.env.SHIO_WEBHOOK_SECRET)
                      .update(raw).digest("hex");
  const got = request.headers.get("x-shio-signature") ?? "";

  const a = Buffer.from(expected), b = Buffer.from(got);
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
    return new Response("bad signature", { status: 401 });
  }

  const event = JSON.parse(raw.toString("utf8"));
  // …revalidate event.url…
  return new Response("ok");
}
```

Set `SHIO_WEBHOOK_SECRET` on the receiver to the same value as the subscription's shared
secret. The `create-shio-app` starter ships this receiver at `/api/revalidate`.

:::danger
An endpoint that skips verification is an **unauthenticated write trigger** reachable by
anyone who guesses the URL. If you are not going to verify the signature, do not expose
the endpoint.
:::

---

## What delivery guarantees, and what it does not

Be honest with your consumer about these: several answers are "unspecified", and that
is a real answer rather than a missing one.

| Question | Answer |
|---|---|
| **When does it fire?** | **After the writing transaction commits.** A publish that rolls back emits nothing. |
| **Is it synchronous?** | No. Delivery runs off the request thread, so a slow or failing subscriber never delays the editor's publish. |
| **Is it retried?** | Yes: **3 attempts** by default, with a linear back-off of 2s × attempt number. Connect timeout 3s, read timeout 5s. |
| **What if all attempts fail?** | The event is **dropped**. There is no dead-letter queue and no replay. |
| **Is ordering guaranteed?** | **No.** Deliveries are dispatched concurrently and retried independently. Two events for the same post can arrive out of order. |
| **Is delivery exactly-once?** | **No, at-most-once per attempt, and a retry can duplicate.** A subscriber that timed out after doing the work still gets retried. |
| **So must my consumer be idempotent?** | **Yes.** Treat the payload as "this route may have changed", not as an instruction to be executed exactly once. |

The practical shape that follows: key your handler on `url` (or `postId`), make
re-running it harmless, and do not derive state from the order events arrive in. If you
need a guaranteed, ordered view of what changed, poll
[`/api/v2/agent/changes`](/shio/docs/agent-surface/) instead: that feed is commit-ordered and
resumable, which is exactly what a webhook is not.

### When a delivery fails

A failed delivery is recorded where it can be read back, not only in the server log. It
appears in `GET /api/v2/agent/diagnostics`, attributed to the site whose content changed,
with the target URL as its subject, so "published but the site never rebuilt" is a
question you can answer.

---

## Tuning

Delivery behaviour is configured instance-wide (see the
[Configuration Reference](/shio/docs/configuration-reference/)):

```properties
shio.webhook.enabled=true
shio.webhook.connect-timeout-millis=3000
shio.webhook.read-timeout-millis=5000
shio.webhook.max-attempts=3
shio.webhook.retry-backoff-millis=2000
```

`shio.webhook.enabled=false` is the master switch: no subscription fires, and none has to
be deleted to make that true.

---

## Related

- [Content Delivery API](/shio/docs/headless/content-delivery-api/): fetching what the event points at
- [Next.js starter](/shio/docs/headless/nextjs-starter/): the receiver this signing scheme matches
- [The agent surface](/shio/docs/agent-surface/): `/agent/changes`, the ordered alternative
- [Administration § Webhooks](/shio/docs/administration-guide/#webhooks): the console screen
- [Configuration Reference](/shio/docs/configuration-reference/): every `shio.webhook.*` setting
