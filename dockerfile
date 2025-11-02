FROM eclipse-temurin:25-jdk

ARG OTEL_AGENT_VERSION=2.6.0

RUN apt-get update && \
    apt-get install -y curl && \
    curl -L -o /opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar && \
    apt-get remove -y curl && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

VOLUME /tmp
ARG JAR_FILE=aratiri-app/target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-javaagent:/opentelemetry-javaagent.jar", "-jar", "/app.jar"]