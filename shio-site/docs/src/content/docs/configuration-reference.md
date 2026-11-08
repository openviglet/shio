---
title: Configuration Reference
description: "Every Viglet Shio configuration property, read from the tree: the instance, the delivery API, the agent surface, files and images, Turing indexing, and the schema."
sidebar:
  order: 2
---

Properties are set in `application.properties` (embedded in the JAR) or in an external file
loaded with `--spring.config.additional-location`. Environment variables and command-line
arguments override both, in the usual Spring Boot order.

Every property below exists in the current release. If a property you remember is missing
from this page, check the two notes: it may have been removed.

---

## The instance

| Property | Default | Description |
|---|---|---|
| `server.port` | `2710` | HTTP listening port. Also accepted on the command line: `java -jar viglet-shio.jar --server.port=8099` |
| `shio.admin.password` | *(unset)* | Sets the `admin` password on startup, skipping the first-access screen. Environment form `SHIO_ADMIN_PASSWORD`; minimum six characters. Never overwrites a password already configured and never prevents startup. See [Installation § Setting the admin password without the console](/docs/installation-guide/#setting-the-admin-password-without-the-console) |
| `shio.url` | `http://localhost:2710` | The instance's own base URL, used wherever an absolute URL is composed |
| `shio.allowedOrigins` | `localhost` | CORS allowed origins |
| `shio.keycloak` | `false` | Authenticate against a Keycloak realm |
| `shio.keycloak-admin-id` | *(empty)* | The realm's admin identifier |
| `shio.multi-tenant` | `false` | Tenant isolation: see [Multi-Tenancy](/docs/multi-tenancy/) |
| `shio.device-auth.redirect-uris` | *(empty)* | Redirect addresses a native app may be sent back to after signing in through this instance, compared exactly. Empty turns the in-app sign-in off. See [Administration § Device tokens](/docs/administration-guide/#device-tokens) |
| `shio.device-auth.code-ttl` | `2m` | How long the one-time code from that sign-in can be exchanged for a device token |
| `server.compression.enabled` | `true` | Response compression |
| `server.compression.mime-types` | `application/json,text/css,application/javascript` | What to compress |
| `server.compression.min-response-size` | `2048` | Minimum size, in bytes |
| `server.tomcat.use-relative-redirects` | `true` | Emit relative redirects |
| `shio.config.system` · `.auth` · `.exchange` | `/system` · `/provider/auth/%s` · `/provider/exchange/%s` | Configuration paths |

:::note[Removed: the JavaScript engine properties]
`shio.website.javascript.engine` and `shio.website.nashorn` **no longer exist**. Shio
renders pages with Handlebars templates, and there is no server-side script engine to
select or tune (the Nashorn dependency is explicitly excluded from the build). Setting
either property has no effect. See [Pages, Layouts & Regions](/docs/website-development/).
:::

---

## Content delivery (`shio.cda.*`)

| Property | Default | Description |
|---|---|---|
| `shio.cda.cache.max-age-seconds` | `60` | `Cache-Control: max-age` on delivery responses; `0` sends `no-cache` |
| `shio.cda.rate-limit.enabled` | `true` | The per-token limiter |
| `shio.cda.rate-limit.capacity` | `120` | Burst a single token may spend at once |
| `shio.cda.rate-limit.refill-tokens` | `120` | Tokens replenished per period |
| `shio.cda.rate-limit.refill-period-seconds` | `60` | Refill cadence |
| `shio.cda.preview.enabled` | `true` | Whether preview tokens can be minted at all |
| `shio.cda.preview.ttl-seconds` | `900` | How long a minted preview token lives when no lifetime is asked for |
| `shio.cda.preview.max-ttl-seconds` | `3600` | The longest lifetime a request may ask for |
| `shio.cda.preview.path` | `/api/preview` | The front end's draft-mode route the preview URL points at |
| `shio.cda.preview.token-param` · `.url-param` | `token` · `url` | The query parameters that carry the token and the page to that route |

The limiter is **per JVM**, on a multi-node deployment each token's effective budget is
multiplied by the node count.

---

## The agent surface (`shio.agent.*`)

Every limit here exists so a caller cannot ask for an unbounded response. A request over the
maximum is clamped, and the response says so.

| Property | Default | Description |
|---|---|---|
| `shio.agent.context.default-budget-tokens` | `4000` | Context-pack size when no `budget` is given |
| `shio.agent.context.max-budget-tokens` | `40000` | Ceiling for `budget=` |
| `shio.agent.context.default-depth` | `3` | Folder-tree depth walked |
| `shio.agent.context.max-nodes` | `1000` | Sitemap rows |
| `shio.agent.batch.max-ops` | `200` | Ops in one `POST /agent/batch` |
| `shio.agent.batch.max-document-ops` | `2000` | Compiled ops in one `apply` |
| `shio.agent.batch.max-session-ops` | `0` | Ops one credential may **write** within the window below; `0` means no ceiling. Set it to stop a looping agent that stays under every per-batch limit. Dry runs are not counted |
| `shio.agent.batch.session-window` | `1h` | The window that ceiling is counted over |
| `shio.agent.read.default-limit` · `.max-limit` | `25` · `200` | `find` / `read` rows |
| `shio.agent.verify.default-limit` · `.max-limit` | `200` · `2000` | Findings per report |
| `shio.agent.verify.max-fetches` | `50` | Requests the delivery check may issue |
| `shio.agent.verify.fetch-budget-millis` | `15000` | Time budget for those fetches |
| `shio.agent.render.allow-fetch` | **`false`** | Whether `/agent/render?url=` may fetch a live URL. Off by default: an endpoint that fetches on the server's behalf needs an operator's consent, not a caller's |
| `shio.agent.render.timeout-millis` | `5000` | Per-fetch timeout |
| `shio.agent.render.max-bytes` | `524288` | Maximum fetched page size |
| `shio.agent.render.write-digests` | `10` | Pages digested when a write asks for `?digest=true` |
| `shio.agent.render.max-write-digests` | `100` | Ceiling for `?digest=<n>` |
| `shio.agent.changes.default-limit` · `.max-limit` | `50` · `500` | Change-feed rows |
| `shio.agent.changes.max-budget-tokens` | `6000` | Estimated-token ceiling on one page of the feed; a larger page is cut and the cursor resumes it. A caller may pass `budget=` up to this, or `0` for none |
| `shio.agent.changes.lag-millis` | `0` | Deliberate lag, for a multi-node install |
| `shio.agent.memory.default-limit` · `.max-limit` | `50` · `500` | Notes returned |
| `shio.agent.memory.max-key-length` · `.max-note-length` | `120` · `2000` | Note size |
| `shio.agent.diagnostics.max-rows` | `500` | Size of the in-memory failure window |
| `shio.agent.diagnostics.default-limit` · `.max-limit` | `25` · `200` | Rows returned |

---

## Files and images

| Property | Default | Description |
|---|---|---|
| `shio.file-source.path` | `store/file_source` | Where uploaded bytes live. With tenancy on, the tenant id prefixes this layout |
| `shio.image-cache.enabled` | `true` | Cache transformed images |
| `shio.image-cache.path` | `store/image_cache` | Where transform results are kept |
| `shio.image-cache.max-memory-entries` | `200` | In-memory entries before falling back to disk |
| `shio.image-transform.max-width` · `.max-height` | `5000` · `5000` | Refuse a transform larger than this |
| `shio.image-transform.max-source-pixels` | `40000000` | Refuse a *source* image larger than this |
| `shio.image-transform.allowed-formats` | `jpg,jpeg,png,gif,webp,avif` | Output formats `?format=` may ask for |
| `shio.image-transform.signing.enabled` | `false` | Require an HMAC on transform URLs |
| `shio.image-transform.signing.secret` | *(empty)* | The signing secret; set it before enabling signing |
| `shio.asset-renditions.enabled` | `true` | Keep an asset's previous bytes when it is replaced, so the replace can be undone |
| `shio.asset-renditions.keep` | `3` | Superseded copies kept per asset; the oldest is dropped |
| `shio.asset-renditions.path` | `store/asset_renditions` | Where they are kept. Outside `file_source` on purpose: nothing here is publicly downloadable. These bytes do not count against a tenant's storage quota |
| `shio.upload-sessions.path` | `store/upload_sessions` | Where the parts of a resumable upload wait until it finishes |
| `shio.upload-sessions.ttl` | `24h` | How long an unfinished upload session is kept before it expires |
| `shio.upload.default-verdict` | `allow` | What an upload of a media type the policy does not name gets |
| `shio.upload.policy.<media-type>` | `text/html`, `application/xhtml+xml` and JavaScript **deny**; `image/svg+xml` **sanitize** | `allow`, `deny` or `sanitize` per media type; `image/*` matches a family. See [Security](/docs/security/) |
| `shio.site-thumbnails.path` | `store/site_thumbnails` | Where each site's home-page picture is kept |
| `shio.site-thumbnails.max-bytes` | `524288` | Largest thumbnail accepted |
| `spring.servlet.multipart.max-file-size` | `1024MB` | Maximum upload |
| `spring.servlet.multipart.max-request-size` | `1024MB` | Maximum request |
| `spring.mvc.async.request-timeout` | `3600000` | Async timeout (1 hour) |

---

## Scheduling and blueprints

| Property | Default | Description |
|---|---|---|
| `shio.schedule.enabled` | `true` | The sweep that fires scheduled publishes and unpublishes |
| `shio.schedule.sweep-interval-millis` | `60000` | How often it looks |
| `shio.blueprint.path` | `store/blueprints` | An extra directory of blueprint packages, beside the built-in ones; marketplace installs land here. Empty means the built-in ones alone |
| `shio.marketplace.enabled` | `true` | Read the package catalogue below. Set `false` on an air-gapped or multi-tenant instance: nothing then leaves the JVM |
| `shio.marketplace.url` | `https://shio.viglet.org/marketplace/marketplace.json` | The catalogue the marketplace lists |
| `shio.marketplace.timeout-millis` | `5000` | How long to wait before listing the built-in packages alone |

---

## Turing indexing (`shio.turing.*`)

**Off by default.** See [Search & Caching](/docs/search-caching/).

| Property | Default | Description |
|---|---|---|
| `shio.turing.enabled` | **`false`** | Index published content into Turing |
| `shio.turing.url` | *(unset)* | The Turing instance |
| `shio.turing.api-key` | *(unset)* | Its API key |
| `shio.turing.default-site` | *(unset)* | Turing SN site for any Shio site not mapped below |
| `shio.turing.sites.<ShioSite>` | *(unset)* | Explicit Shio site → Turing SN site mapping (case-insensitive) |
| `shio.turing.source-app` | `shio` | The `source_apps` stamp on indexed documents |
| `shio.turing.de-index-on-unpublish` | `true` | De-index when a post is unpublished |
| `shio.turing.max-attempts` | `3` | Attempts per push, the first included; `1` disables retrying |
| `shio.turing.retry-backoff` | `1s` | Wait before the second attempt, doubled before each one after it |
| `shio.turing.extract-text` | `true` | Ask Turing to read the text of a published attachment so it answers a search |
| `shio.turing.extract-types` | `pdf,doc,docx,odt,rtf,txt,md,ppt,pptx,odp,xls,xlsx,ods` | File extensions whose text is asked for |
| `shio.turing.extract-max-size` | `33554432` (32 MB) | Larger files are indexed by their metadata alone; `0` removes the limit |
| `shio.turing.extract-timeout` | `60s` | How long to wait for one file's text before indexing it without |

A site with neither a mapping nor a `default-site` is simply not indexed, and that is not an
error.

---

## Sites and sign-in

| Property | Default | Description |
|---|---|---|
| `shio.sites.host-domain` | *(empty)* | Serve each site at `<site-furl>.<host-domain>`. Empty turns site hosts off. Environment form `SHIO_SITES_HOST_DOMAIN`; the wildcard DNS, certificate and proxy are yours. See [Administration](/docs/administration-guide/) |
| `shio.sites.host-scheme` | `https` | The scheme a site host's canonical URL is written with |
| `shio.required-viglet-client` | *(empty)* | Make this instance a dedicated silo for one client: only logins and tokens whose `viglet_client` claim equals this value are admitted. Empty means a shared instance with no restriction. Environment form `SHIO_REQUIRED_VIGLET_CLIENT` |

---

## History, events and metering

| Property | Default | Description |
|---|---|---|
| `shio.versions.keep` | `50` | Revisions kept per post; older ones are pruned. See [Content Lifecycle](/docs/content-lifecycle/) |
| `shio.versions.capture-on-save` | `false` | Also capture a revision when a draft is saved, not only when it is published |
| `shio.events.enabled` | `true` | Push changes to open screens over `GET /api/v2/events` (Server-Sent Events) |
| `shio.events.poll-millis` | `1000` | How often the open streams are checked for new changes |
| `shio.events.heartbeat-millis` | `15000` | Idle time before a keep-alive is sent, under the timeout of common proxies |
| `shio.events.timeout-millis` | `1800000` | How long one connection lives (30 minutes); the client reconnects and resumes where it stopped |
| `shio.events.page-limit` | `100` | Feed entries read per page in one pass |
| `shio.events.max-pages-per-pass` | `5` | Pages read for one stream in one pass, so a busy reader cannot hold the others |
| `shio.meter.enabled` | `true` | Count API calls per tenant and surface; read the result from `shio://usage` |
| `shio.meter.flush-interval-millis` | `60000` | How often counts are written to the database. Also the most a crash can lose |
| `shio.meter.export-on-rollover` | `false` | Hand each closed day to a registered billing exporter. Off, nothing leaves the instance |

---

## Webhooks

| Property | Default | Description |
|---|---|---|
| `shio.webhook.enabled` | `true` | Send outbound webhooks. See [Webhooks](/docs/webhooks/) |
| `shio.webhook.connect-timeout-millis` | `3000` | Connect timeout per delivery attempt |
| `shio.webhook.read-timeout-millis` | `5000` | Response timeout per attempt |
| `shio.webhook.max-attempts` | `3` | Attempts per target before giving up |
| `shio.webhook.retry-backoff-millis` | `2000` | Wait between attempts, multiplied by the attempt number |

---

## Database

### H2 (development default)

```properties
spring.datasource.url=jdbc:h2:file:./store/db/shioDB;DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
```

:::caution[Those two H2 URL parameters are load-bearing]
`DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true` must stay. The schema uses
lowercase identifiers (including the tenant discriminator column) and without them H2
folds names to upper case, which surfaces as `Column "..." not found` at startup.
:::

### PostgreSQL

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/shio
spring.datasource.username=shio
spring.datasource.password=shio
spring.datasource.driver-class-name=org.postgresql.Driver
```

### MariaDB / MySQL

```properties
spring.datasource.url=jdbc:mariadb://localhost:3306/shio
spring.datasource.username=shio
spring.datasource.password=shio
spring.datasource.driver-class-name=org.mariadb.jdbc.Driver
```

The dialect is inferred from the URL; set `spring.jpa.properties.hibernate.dialect` only if
you have a reason to override it. Shio ships no Oracle driver: the schema is verified on
PostgreSQL, MariaDB and H2 by integration tests.

---

## Schema and JPA

| Property | Value | Description |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | **`none`** | **The schema belongs to Liquibase.** Do not change this |
| `spring.liquibase.enabled` | `true` | Run migrations at startup |
| `spring.liquibase.change-log` | `classpath:db/changelog/db.changelog-master.yaml` | The master changelog |
| `spring.jpa.show-sql` | `false` | Log SQL |
| `spring.jpa.properties.hibernate.format_sql` | `false` | Format logged SQL |
| `spring.jpa.properties.hibernate.generate_statistics` | `false` | Hibernate statistics |
| `spring.jpa.properties.jakarta.persistence.sharedCache.mode` | `ALL` | JPA shared cache mode |

An upgrade is a restart: migrations run on boot, so a rolling restart *is* a migration.
Never work around a schema problem by setting `ddl-auto` to `update`: that produces a
database which no longer matches the changelogs, and the next real migration fails.

---

## Logging

| Property | Default | Description |
|---|---|---|
| `logging.level.com.viglet` | `INFO` | Application log level |
| `logging.level.org.springframework` | `INFO` | Spring log level |
| `logging.level.org.hibernate.SQL` | `INFO` | SQL logging |
| `logging.file.name` | `store/logs/shio.log` | Log file |
| `logging.logback.rollingpolicy.max-file-size` | `25MB` | Rotation size |
| `logging.logback.rollingpolicy.max-history` | `10` | Files kept |

Asynchronous failures , webhook deliveries, image transforms, scheduled publishes, 5xx ,
are also readable at `GET /api/v2/agent/diagnostics`, which is usually faster than grepping
the log.

---

## Static resources

| Property | Default | Description |
|---|---|---|
| `spring.web.resources.static-locations` | `classpath:/public/` | Where the built console is served from |
| `spring.h2.console.enabled` | `false` | The H2 web console |
| `spring.h2.console.path` | `/h2` | Its path |
| `spring.output.ansi.enabled` | `always` | Coloured console output |

---

## Mail

| Property | Default | Description |
|---|---|---|
| `spring.mail.host` | `localhost` | SMTP host |
| `spring.mail.port` | `587` | SMTP port |
| `spring.mail.username` | `localhost@localhost` | SMTP username |
| `spring.mail.password` | `password` | SMTP password |
| `spring.mail.properties.mail.smtp.auth` | `true` | SMTP authentication |
| `spring.mail.properties.mail.smtp.starttls.enable` | `true` | STARTTLS |
| `spring.mail.properties.mail.smtp.starttls.required` | `true` | Require STARTTLS |
| `spring.mail.properties.mail.smtp.ssl.enable` | `false` | SSL |
| `spring.mail.test-connection` | `false` | Test the connection at startup |

SMTP can also be configured in the console, which writes the same settings.

---

## Management

| Property | Default | Description |
|---|---|---|
| `management.endpoints.web.exposure.include` | `*` | Exposed Actuator endpoints, narrow this in production |

---

## Profiles

| Profile | Purpose |
|---|---|
| `production` | The default |
| `development` | Extra logging |
| `ui-dev` | Serve the API only, with the console running under a separate Vite dev server |

```properties
spring.profiles.active=production
```

---

## Related Pages

| Page | Description |
|---|---|
| [Installation Guide](/docs/installation-guide/) | Docker, JAR, or build from source |
| [Multi-Tenancy](/docs/multi-tenancy/) | `shio.multi-tenant` and what it turns on |
| [Search & Caching](/docs/search-caching/) | The `shio.turing.*` block in context |
| [Security](/docs/security/) | Origins, tokens, CSRF |
| [Architecture Overview](/docs/architecture-overview/) | Deployment topologies |
