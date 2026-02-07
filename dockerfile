# Stage 1: Build
FROM maven:3.9.12-eclipse-temurin-25 AS builder
WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:25-jdk-jammy
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 2100
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
