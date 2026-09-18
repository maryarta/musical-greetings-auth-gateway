# Build context must be /backend, not /backend/auth-service.
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY . .

# Normalize Windows line endings and make the wrapper executable.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew :auth-service:bootJar --no-daemon

RUN JAR_FILE="$(find auth-service/build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' -print -quit)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
