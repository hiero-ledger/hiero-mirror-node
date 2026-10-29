FROM eclipse-temurin:25.0.4.1_1-jre-resolute@sha256:41d9263c0d5b2a74d89af6e29e73b04298bc65e3cb705d03e9e2271ef49fef84 AS builder
WORKDIR /app
COPY build/libs/*.jar ./
RUN java -Djarmode=tools -jar *.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25.0.4.1_1-jre-resolute@sha256:41d9263c0d5b2a74d89af6e29e73b04298bc65e3cb705d03e9e2271ef49fef84
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80"
EXPOSE 8083
HEALTHCHECK --interval=10s --retries=3 --start-period=50s --timeout=5s CMD ["java", "-cp", "/app/healthcheck.jar", "org.hiero.mirror.common.healthcheck.HealthCheckClient", "http://localhost:8083/actuator/health/liveness"]
WORKDIR /app

# Copy artifacts
COPY --chown=1000:1000 --from=builder /app/extracted/dependencies/ ./
COPY --chown=1000:1000 --from=builder /app/extracted/spring-boot-loader/ ./
COPY --chown=1000:1000 --from=builder /app/extracted/application/ ./
COPY --chown=1000:1000 --from=builder /app/extracted/application/BOOT-INF/lib/common-*.jar ./healthcheck.jar

# install OS updates, and remove pebble due to vulnerability
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/bin/pebble && rm -rf /var/lib/pebble
USER 1000:1000

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
