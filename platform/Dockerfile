# syntax=docker/dockerfile:1
# Build stage: Maven wrapper on a JDK. Tests are skipped here on purpose: they need Docker (Testcontainers) and run in CI.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Dependencies first, so a source change does not download them again
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && cp target/*-SNAPSHOT.jar /build/app.jar

# Run stage: JRE only, not root. curl is for the compose healthcheck.
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home app
USER app
WORKDIR /home/app
COPY --from=build /build/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
