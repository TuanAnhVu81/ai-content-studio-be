# Stage 1: Build stage
FROM maven:3.8.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Run stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/ai-content-studio-be-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
