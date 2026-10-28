# SH1249: Shio as a GraalVM CE native image, measured against the JAR

**Verdict: no-go for the shipped image, today.** The native image builds and boots, and it is
much faster and smaller than the JVM. But it answers 500 on any reflective path the tracing run
did not happen to visit, with nothing in the log, and the first route probed outside the
measurement replay was one of them. Until the reachability metadata is generated from something
that covers the whole surface (the test suite, not a replay), that is a correctness regression,
and "Don't optimize a benchmark by degrading correctness" binds. The cheap JVM levers measured
beside it are a **go**: they cost one flag and an extra build step and keep every path working.

Measured on 2026-10-28, on one Windows 11 workstation, with Docker through freewilly
(28 CPUs, 32 GB given to the engine). GraalVM **Community Edition** only: GPLv2 with the
Classpath Exception, no Oracle GraalVM.

## The table

Medians of three cold runs per variant, each on a fresh container and a fresh store volume.
Times are from `docker run` to the first 200; RSS is `VmRSS` of pid 1 (the java process or
the native binary), read ten idle seconds after the CDA answered, and again after the replay.
The replay is 600 requests, 8 at a time: MCP `tools/list` and `tools/call shio_context`, and
CDA `/site`, `/post-type`, `/query?size=20` and `/search?q=viglet`, with the CDA rate limit off.

| Variant | Image | First 200 `/mcp` | First 200 CDA | Idle RSS | RSS after replay | Peak RSS | p50 | p95 |
|---|---|---|---|---|---|---|---|---|
| JVM as shipped (`-Xms512m -Xmx512m`, G1) | 575 MB | 19.3 s | 19.6 s | 999 MB | 1097 MB | 1097 MB | 16 ms | 119 ms |
| JVM, `-XX:+UseSerialGC -Xms64m -Xmx512m` | 575 MB | 17.0 s | 17.2 s | 707 MB | 812 MB | 812 MB | 12 ms | 99 ms |
| JVM, AppCDS + the same SerialGC flags | 575 MB | 13.7 s | 13.9 s | 726 MB | 769 MB | 775 MB | 14 ms | 100 ms |
| Native (GraalVM CE 25, Spring AOT) | 229 MB | 4.2 s | 4.3 s | 363 MB | 359 MB | 380 MB | 9 ms | 109 ms |

Building the native image: 5 min 48 s for the whole Maven build in the container, of which
native-image itself took 3 to 4.5 min, at a **peak build RSS of 18 GB** (17.98 and 18.32 GB on
two builds). 61,438 types reachable; 163 MB of code and 147 MB of image heap in the binary.
A CI runner with 7 GB (GitHub's standard Linux runner) cannot build it.

Runs vary: the shipped JVM's first 200 ranged from 18.7 s to 25.1 s. The ordering between
variants held in every run. "Started Shio in 2.3 s" in the native log is the context refresh;
the first 200 comes about 2 s later, after the first-time configuration and Liquibase on the
empty store, which is the playground case this was asked for.

## The risks, each as works, needs hints, or blocks

| Risk | Result |
|---|---|
| GraalVM CE **21** | **Blocks.** Analysis fails: Spring Data 4 initializes `SerializableLambdaReader` at build time and drags commons-logging, SLF4J and Logback in with it. Spring Boot 4 targets GraalVM 25, and native-build-tools 1.1.x ships metadata CE 21 cannot read. The spike moved to CE 25 (Java 25), which the baseline is not. |
| Embedded Artemis and Netty | **Not present.** No Artemis jar is in `viglet-shio.jar`; the Dockerfile's reason for pinning Java 21 is stale. Netty is on the classpath and the image built and served. |
| Hazelcast | **Not exercised.** `hazelcast-all` and `hazelcast.xml` ship, but no instance starts on either runtime (nothing listens on 5701), as `ShCdaCacheControlFilter` already says. |
| JGit | **Not exercised.** On the classpath; nothing in `shio-app/src/main` imports it. |
| jai-imageio, webp-imageio | **Not exercised.** A fresh store has no image to transform, and the static-file listing a probe needs failed first (below). WebP is a JNI library, the riskiest of the set, and is unproven. |
| Handlebars helpers | **Not exercised.** A fresh store has no site to render. |
| Liquibase changelogs | **Needs hints.** Fails at boot: changelog preconditions are bound by reflection (`ColumnExistsPrecondition.setTableName`). Covered by the tracing agent's output. |
| Hibernate under Spring AOT | **Needs hints and a build change.** Hibernate finds ByteBuddy by ServiceLoader and cannot define classes in an image. Fixed by excluding that service (`-H:ServiceLoaderFeatureExcludeServices`) and **build-time entity enhancement** with lazy initialization, because Shio has lazy associations. That enhancement changes the entity bytecode, so shipping it means testing the JVM with enhanced entities too. |
| GraphQL on the CDA | **Works** (introspection answered 200 on both). |
| Untraced reflection | **Blocks.** `GET /api/v2/staticfile` returns 500 because `ShStaticFileItem[]` was never registered; the replay never listed files, so the agent never saw it. Only `-XX:MissingRegistrationReportingMode=Warn` names it. |
| Runtime log levels | **Regression.** `LOGGING_LEVEL_*` has no effect in the image (the AOT-processed Logback model is fixed at build time), so the 500 above leaves no trace. |

## What a go would need

1. Metadata from the **test suite** run under the agent (native-build-tools' `-Dagent=true`),
   not from a replay, so every controller the suite drives is registered; and a conformance run
   against the native image as the gate that proves it.
2. A build host with 20 GB or more, and Java 25 for the native lane only.
3. Hibernate enhancement adopted for the JVM as well, or the two lanes run different entity
   bytecode.

## What it would buy

Boot from about 19 s to about 4 s and RSS from about 1 GB to about 360 MB: four tenants'
worth of memory per JVM. That matters for an ephemeral playground tenant and nowhere else
today. For a long-running instance, AppCDS plus SerialGC already takes boot to 13.7 s and RSS
to about 730 MB at the same p95, for one build step and one flag. A native image that only beat
the untuned JVM would have proved nothing; against the tuned one it still wins on boot and
memory, and the cost is the correctness risk above.

## Reproducing it

Everything is in `scripts/native-spike/`, and the shipped `Dockerfile` is unchanged.

```
./mvnw -pl shio-app -am clean package -DskipTests -Dskip.npm=true
docker build -f scripts/native-spike/Dockerfile.jvm -t shio-jvm:spike .
# the tracing run: the same boot and replay on a GraalVM 25 JVM, writing agent-metadata/
node scripts/native-spike/measure.mjs agent ghcr.io/graalvm/native-image-community:25 --runs 1 \
  --docker-arg -v --docker-arg "$PWD/shio-app/target/viglet-shio.jar:/app/app.jar:ro" \
  --docker-arg -v --docker-arg "$PWD/scripts/native-spike/agent-metadata:/agent" \
  --docker-arg -w --docker-arg /app --docker-arg --entrypoint --docker-arg java -- \
  -agentlib:native-image-agent=config-output-dir=/agent,config-write-period-secs=5 -jar /app/app.jar
node scripts/native-spike/prune-agent-metadata.mjs scripts/native-spike/agent-metadata/reachability-metadata.json
docker build -f scripts/native-spike/Dockerfile.native -t shio-native:spike .
powershell -File scripts/native-spike/measure-all.ps1
node scripts/native-spike/probe.mjs shio-native:spike
```

The `native-spike` Maven profile in `shio-app/pom.xml` is opt-in: Spring AOT, the Hibernate
enhancement, and native-image with the agent's metadata. `agent-metadata/` is generated and
git-ignored.
