# Build from repository root: docker build -f examples/1/Dockerfile.app .
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :app:installDist --no-daemon

FROM eclipse-temurin:25-jdk
WORKDIR /opt/app
COPY --from=builder /workspace/app/build/install/app/ ./
ENV JAVA_TOOL_OPTIONS=""
ENTRYPOINT ["/opt/app/bin/app"]
