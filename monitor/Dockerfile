FROM eclipse-temurin:25-jre-resolute AS builder
WORKDIR /app
COPY build/libs/*.jar ./
RUN java -Djarmode=tools -jar *.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-resolute
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80"
EXPOSE 8082
HEALTHCHECK --interval=10s --retries=5 --start-period=60s --timeout=2s CMD curl -f http://localhost:8082/actuator/health/liveness
WORKDIR /app

# Copy artifacts
COPY --chown=1000:1000 --from=builder /app/extracted/dependencies/ ./
COPY --chown=1000:1000 --from=builder /app/extracted/spring-boot-loader/ ./
COPY --chown=1000:1000 --from=builder /app/extracted/application/ ./

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*    # install OS updates
USER 1000:1000

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
