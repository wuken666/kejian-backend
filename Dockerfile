FROM maven:3.9.13-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml ./

COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /app/target/teacher-feedback-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
