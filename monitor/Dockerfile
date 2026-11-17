FROM eclipse-temurin:25.0.5_8-jre-resolute@sha256:b6e00330337143a2c1a20f1c1c693d4b6ce9a3c30ade8b9d03fc68cc9e3871e7 AS builder
WORKDIR /app
COPY build/libs/*.jar ./
RUN java -Djarmode=tools -jar *.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25.0.5_8-jre-resolute@sha256:b6e00330337143a2c1a20f1c1c693d4b6ce9a3c30ade8b9d03fc68cc9e3871e7
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80"
EXPOSE 8082
HEALTHCHECK --interval=10s --retries=5 --start-period=60s --timeout=5s CMD ["java", "-cp", "/app/healthcheck.jar", "org.hiero.mirror.common.healthcheck.HealthCheckClient", "http://localhost:8082/actuator/health/liveness"]
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
