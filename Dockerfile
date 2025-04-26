FROM eclipse-temurin:21-jdk-centos7 as shiobuild
WORKDIR /app
COPY . .
RUN ./mvnw shio-app:build

FROM eclipse-temurin:21-jdk-centos7
WORKDIR /app
ENV JAVA_OPTS=${JAVA_OPTS:-'-Xmx512m'}
ENV DEBUG_OPTS=${DEBUG_OPTS}
ENV PORT=${PORT:-2710}

RUN useradd --system --create-home --uid 1001 --gid 0 java

RUN sh -c 'mkdir -p /app/store'

COPY --from=shiobuild  /app/shio-app/build/libs/shio-app.jar /app/app.jar
RUN sh -c 'touch /app/app.jar'
RUN sh -c 'chown -R java /app'

VOLUME /tmp
VOLUME /app/store

USER java

EXPOSE ${PORT:-2710}

CMD java ${JAVA_OPTS} --add-modules java.se \
  --add-exports java.base/jdk.internal.ref=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.management/sun.management=ALL-UNNAMED \
  --add-opens jdk.management/com.ibm.lang.management.internal=ALL-UNNAMED \
  --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED \
  ${DEBUG_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar
