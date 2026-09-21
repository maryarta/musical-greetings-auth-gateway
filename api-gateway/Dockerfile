FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY . .

RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

RUN ./gradlew :api-gateway:bootJar --no-daemon

RUN JAR_FILE="$(find api-gateway/build/libs -maxdepth 1 -name '*.jar' ! -name '*plain.jar' -print -quit)" \
    && test -n "$JAR_FILE" \
    && cp "$JAR_FILE" /workspace/app.jar

FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build /workspace/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
