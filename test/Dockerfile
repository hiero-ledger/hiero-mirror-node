FROM eclipse-temurin:25.0.4_7-jre-resolute@sha256:bba6425238c09942f54837cff7e63b94f977130f0dba42357a6d83967bbc3c69

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