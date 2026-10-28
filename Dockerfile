# Multi-stage build for Viglet Shio CMS.
# Ported from viglet/cloud docker/Dockerfile.shio — the `viglet/jdk-build`
# base (eclipse-temurin:21-jdk + git) is inlined here so the build is
# self-contained and needs no externally published base image.
#
# Java 21: the release every suite runs on. The reason this file used to give (embedded Artemis
# crashing on Java 25) no longer applies, since no Artemis jar ships in viglet-shio.jar (SH1249).
# A move to 25 is its own change, measured, not a side effect of this one.
FROM eclipse-temurin:21-jdk AS build
RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*
WORKDIR /src
ARG REPO_BRANCH=2026.3
ARG CACHE_BUST=none
# Reference CACHE_BUST in the RUN so a new SHA invalidates the git clone layer.
# `github_token` is a BuildKit secret. It's only present at build time via the
# runtime mount and never lands in any image layer. Optional — falls back to
# anonymous clone if the secret is empty.
RUN --mount=type=secret,id=github_token,required=false \
    echo "cache_bust=${CACHE_BUST}" && \
    if [ -s /run/secrets/github_token ]; then \
        URL="https://x-access-token:$(cat /run/secrets/github_token)@github.com/openviglet/shio.git"; \
    else \
        URL="https://github.com/openviglet/shio.git"; \
    fi && \
    git clone --depth 1 --branch ${REPO_BRANCH} "$URL" .
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/src/shio-react/node_modules \
    --mount=type=cache,target=/src/shio-react/node \
    ./mvnw clean package -DskipTests 2>&1

FROM eclipse-temurin:21-jre
ARG CACHE_BUST=none
LABEL org.opencontainers.image.revision=$CACHE_BUST
RUN groupadd -r java && useradd -r -g java -u 1001 java
WORKDIR /app
COPY --from=build /src/shio-app/target/viglet-shio.jar app.jar
# SH1260 — class data sharing, trained on one context refresh against an empty store, which is
# then deleted. Measured in SH1249's spec under docs/specs: the first 200
# from 19.3 s to 13.7 s and idle RSS from 999 MB to 726 MB, at the same p95. The archive sits in
# the ENTRYPOINT rather than JAVA_OPTS, so overriding the heap flags keeps it.
RUN java -Djarmode=tools -jar app.jar extract --destination cds \
 && java -XX:ArchiveClassesAtExit=cds/app.jsa -Dspring.context.exit=onRefresh \
         -Dlogging.config=classpath:logback-spring-console.xml -jar cds/app.jar \
 && rm -rf /app/store /app/app.jar && mkdir -p /app/store && chown -R java:java /app
USER java
VOLUME ["/app/store"]
EXPOSE 2710
# SerialGC and a heap that grows from 64 MB: a single-instance container does not need G1's
# threads or a 512 MB heap committed at start. A large deployment overrides JAVA_OPTS.
ENV JAVA_OPTS="-XX:+UseSerialGC -Xms64m -Xmx512m"
ENV DEBUG_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java -XX:SharedArchiveFile=/app/cds/app.jsa ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dlogging.config=classpath:logback-spring-console.xml -jar /app/cds/app.jar"]
