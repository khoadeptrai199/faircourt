# One Dockerfile for every backend service (F18, AD-13). Build arg MODULE selects the service, e.g.
#   docker compose build playmatch-service      (compose.yaml passes MODULE)
# Multi-arch base images: `docker buildx imagetools inspect` showed linux/amd64 + linux/arm64 for both (2026-11-09).
# Host Java/Maven versions do not matter: everything builds inside these images.
# The Maven image has no JDK-patch tag, so it is pinned by digest (contains Temurin JDK 25.0.4.1, checked 2026-11-09).

FROM maven:3.9.16-eclipse-temurin-25-noble@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS deps
WORKDIR /workspace
# Only the poms, so the dependency layer is reused until a pom changes (shared by all services' builds).
COPY pom.xml .
COPY libs/common-web/pom.xml libs/common-web/
COPY libs/matching/pom.xml libs/matching/
COPY services/gateway/pom.xml services/gateway/
COPY services/auth-service/pom.xml services/auth-service/
COPY services/player-service/pom.xml services/player-service/
COPY services/playmatch-service/pom.xml services/playmatch-service/
RUN mvn -B -q dependency:go-offline

# Test image for the whole reactor: `docker compose --profile test run --rm --build backend-test`
FROM deps AS test
COPY . .
CMD ["mvn", "-B", "verify"]

FROM deps AS build
COPY . .
ARG MODULE
RUN test -n "$MODULE" && mvn -B -q -DskipTests -pl services/${MODULE} -am package \
    && cp services/${MODULE}/target/${MODULE}.jar /workspace/app.jar

FROM eclipse-temurin:25.0.5_8-jre-noble AS runtime
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
