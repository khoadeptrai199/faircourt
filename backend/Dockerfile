# Multi-arch base images: `docker buildx imagetools inspect` showed linux/amd64 + linux/arm64 for both (2026-11-09).
# Host Java/Maven versions do not matter: everything builds inside these images.
# The Maven image has no JDK-patch tag, so it is pinned by digest (contains Temurin JDK 25.0.4.1, checked 2026-11-09).

FROM maven:3.9.16-eclipse-temurin-25-noble@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS deps
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Test image: `docker compose --profile test run --rm --build backend-test`
FROM deps AS test
COPY src ./src
CMD ["mvn", "-B", "verify"]

FROM deps AS build
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:25.0.5_8-jre-noble AS runtime
WORKDIR /app
COPY --from=build /workspace/target/faircourt-backend.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
