FROM eclipse-temurin:21-jre-noble AS builder
WORKDIR /app
COPY build/libs/*.jar ./
RUN java -Djarmode=layertools -jar *.jar extract

FROM eclipse-temurin:21-jre-noble
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80"
EXPOSE 8082
HEALTHCHECK --interval=10s --retries=5 --start-period=60s --timeout=2s CMD curl -f http://localhost:8082/actuator/health/liveness
WORKDIR /app

# Copy artifacts
COPY --chown=1000:1000 --from=builder /app/dependencies/ ./
COPY --chown=1000:1000 --from=builder /app/snapshot-dependencies/ ./
RUN true  # Workaround https://github.com/moby/moby/issues/37965
COPY --chown=1000:1000 --from=builder /app/spring-boot-loader/ ./
COPY --chown=1000:1000 --from=builder /app/application/ ./
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*    # install OS updates
USER 1000:1000

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
