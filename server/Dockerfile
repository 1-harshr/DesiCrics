# The DesiCrics server as one container, for Cloud Run or Fly.io.
# Build the jar first (core's Android target needs the Android SDK, so the image doesn't compile):
#   ./gradlew :server:buildFatJar && docker build -t desicrics-server server
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY build/libs/server-all.jar server.jar

# Cloud Run and Fly set PORT; the server reads it (ServerConfig).
ENV PORT=8080
EXPOSE 8080
# Leave headroom in small instances: the heap follows the container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "server.jar"]
