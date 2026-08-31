FROM eclipse-temurin:25.0.4_7-jre-resolute@sha256:71bfb73662d4146bdc9ee2f97418a764d5ab4e83f4e90baec3b3dcda14071007

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=80"
WORKDIR /app
COPY --chown=1000:1000 build/libs/*-all.jar test.jar
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get upgrade -y --no-install-recommends \
    && rm -rf /var/lib/apt/lists/*
USER 1000:1000

ENV ENTRYPOINT_COMMAND="java -jar /app/test.jar"
ENTRYPOINT ["java", "-jar", "/app/test.jar"]