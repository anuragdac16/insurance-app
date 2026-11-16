# syntax=docker/dockerfile:1

# ---- build stage: compile with Maven + JDK 25 ------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
# Download dependencies first so this layer is cached until pom.xml changes.
RUN --mount=type=cache,target=/root/.m2,id=insurance-app-maven,sharing=locked mvn -B -ntp dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,id=insurance-app-maven,sharing=locked mvn -B -ntp -DskipTests package

# ---- runtime stage: small JRE image, non-root user -------------------------
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 appuser
USER appuser
WORKDIR /app
COPY --from=build /workspace/target/insurance-app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
