# Stage 1: Build
FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /app
ARG APP_VERSION=unknown

COPY gradlew gradlew.bat settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x ./gradlew
RUN ./gradlew --no-daemon dependencies -PappVersion="${APP_VERSION}"

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test -PappVersion="${APP_VERSION}"

# Stage 2: Runtime
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
ARG APP_VERSION=unknown
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="aratiri" \
      org.opencontainers.image.description="A multi-user Bitcoin Lightning and on-chain middleware platform" \
      org.opencontainers.image.source="https://github.com/DiegoYegros/aratiri" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.version="${APP_VERSION}"
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 2100
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
