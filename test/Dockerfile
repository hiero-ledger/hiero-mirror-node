FROM eclipse-temurin:25.0.4_7-jre-resolute@sha256:23adeb62d27f7fefcfe6b81f6f1e3809539159f70a435ba70ac14c192a8cb75e

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80"
WORKDIR /app
COPY --chown=1000:1000 build/libs/*-all.jar test.jar

# install OS updates, and remove pebble due to vulnerability
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/bin/pebble && rm -rf /var/lib/pebble
USER 1000:1000

ENV ENTRYPOINT_COMMAND="java -jar /app/test.jar"
ENTRYPOINT ["java", "-jar", "/app/test.jar"]