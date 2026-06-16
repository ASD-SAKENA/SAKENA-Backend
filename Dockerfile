# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only what's needed to resolve dependencies first, so this layer is cached
# unless the build files change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now copy sources and build the executable jar.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system sakena && useradd --system --gid sakena sakena

COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown -R sakena:sakena /app
USER sakena

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
