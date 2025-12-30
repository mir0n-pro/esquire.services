# Stage 1: Build the application
# Use the full JDK for building
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Copy only Maven files first to leverage Docker layer caching for dependencies
COPY .mvn/ .mvn
COPY mvnw .
COPY pom.xml .
# On Windows, ensure line endings are handled if running in a Linux container
RUN ./mvnw dependency:go-offline

# Copy source and build the final JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Create the lean runtime image
# Use a JRE-only image for a smaller footprint (approx 150-200MB vs 500MB+)
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create a non-root user for 2025 security standards
RUN useradd -ms /bin/bash springuser
USER springuser

# Copy the built JAR from the builder stage
COPY --from=builder ./app/target/*.jar biztree.jar