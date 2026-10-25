FROM eclipse-temurin:25.0.4.1_1-jre-resolute@sha256:41d9263c0d5b2a74d89af6e29e73b04298bc65e3cb705d03e9e2271ef49fef84

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