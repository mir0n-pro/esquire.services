# =============================================================================
# Multi-stage build: Maven inside Docker → no local JDK/Maven needed
# Build context must be esquire.services/ (parent POM directory)
# =============================================================================

# --- Stage 1: Build with Maven ---
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Install Maven
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

# Copy parent POM + all modules for dependency resolution
COPY pom.xml .
COPY common/ common/
COPY gateway/ gateway/
COPY bizTree/ bizTree/
COPY enyMan/ enyMan/
COPY pacMan/ pacMan/
COPY keySmith/ keySmith/

# Build common first (dependency), then gateway
RUN mvn clean package -pl common,gateway -am -DskipTests -q

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd -ms /bin/bash springuser
USER springuser

COPY --from=builder /build/gateway/target/*.jar app.jar

#ENTRYPOINT ["java", "-jar", "app.jar"]
