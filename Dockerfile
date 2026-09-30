# -------------------------------------------------------------
# Stage 1: Build Application with Maven
# -------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

# Cache Maven dependencies
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Build application artifact
COPY src src
RUN ./mvnw package -DskipTests -B

# Extract Spring Boot layered jars for optimized Docker layer caching
RUN java -Djarmode=layertools -jar target/shortlink-service-*.jar extract

# -------------------------------------------------------------
# Stage 2: Minimal Distroless / Hardened Runtime
# -------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Run as non-privileged user (Amazon Security standard)
RUN groupadd -r amazonapp && useradd -r -g amazonapp -u 1001 amazonapp
USER amazonapp

# Copy layers from builder
COPY --from=builder /workspace/dependencies/ ./
COPY --from=builder /workspace/spring-boot-loader/ ./
COPY --from=builder /workspace/snapshot-dependencies/ ./
COPY --from=builder /workspace/application/ ./

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
