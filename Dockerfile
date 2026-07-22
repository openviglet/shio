# Multi-stage build for Viglet Shio CMS.
# Ported from viglet/cloud docker/Dockerfile.shio — the `viglet/jdk-build`
# base (eclipse-temurin:21-jdk + git) is inlined here so the build is
# self-contained and needs no externally published base image.
#
# Java 21 (NOT 25): embedded Artemis 2.43.0 (via Netty) crashes on Java 25 with
# "Cannot clean arbitrary ByteBuffer instances" (CleanerJava25) while loading its
# NIO journal, which takes down the broker and every JMS listener.
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
RUN mkdir -p /app/store && chown -R java:java /app
USER java
VOLUME ["/app/store"]
EXPOSE 2710
ENV JAVA_OPTS="-Xmx512m -Xms512m"
ENV DEBUG_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -Dlogging.config=classpath:logback-spring-console.xml -jar /app/app.jar"]
