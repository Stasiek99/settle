# syntax=docker/dockerfile:1

# ---- Build: compile, package and split the jar into layers ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Resolve dependencies first so this layer is cached until the pom changes.
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline

# Tests need Docker (Testcontainers), so they run in CI, not inside the image build.
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
    && cp target/settle-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Runtime: only a JRE, the extracted layers and a non-root user ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /application

RUN addgroup -S settle && adduser -S -G settle -H settle

# Ordered from least to most likely to change, so rebuilds reuse the cached layers.
COPY --from=build --chown=settle:settle /build/extracted/dependencies/ ./
COPY --from=build --chown=settle:settle /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=settle:settle /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=settle:settle /build/extracted/application/ ./

USER settle

# Datasource and JWT_SECRET are supplied at runtime, never baked into the image:
#   SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, JWT_SECRET
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=5 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
