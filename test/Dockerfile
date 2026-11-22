FROM eclipse-temurin:25.0.5_8-jre-resolute@sha256:480ef64e4540d45b0301e8afb7873bd3fa61ce93c5f66273eb569420718f38e2

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80 -XX:-UseBASE64Intrinsics"
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