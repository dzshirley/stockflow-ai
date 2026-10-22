FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S stockflow && adduser -S stockflow -G stockflow
WORKDIR /app
COPY --from=build /workspace/target/stockflow-api-*.jar app.jar
USER stockflow
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
