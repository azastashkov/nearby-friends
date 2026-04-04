FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY load-client load-client
RUN chmod +x gradlew && ./gradlew :load-client:bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/load-client/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
