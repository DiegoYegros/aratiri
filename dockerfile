FROM eclipse-temurin:25-jdk
VOLUME /tmp
ARG JAR_FILE=aratiri-app/target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]