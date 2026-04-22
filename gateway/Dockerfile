# Stage 1: Build the application
# Use the full JDK for building
#FROM eclipse-temurin:21-jdk-jammy AS builder
#WORKDIR /app

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create a non-root user for security
RUN useradd -ms /bin/bash springuser
USER springuser

# Copy the JAR file that was built locally on your machine into the container
# This assumes you ran 'mvn clean package' before running 'docker compose build'
COPY target/*.jar app.jar

# Define how to start the application
#ENTRYPOINT ["java", "-jar", "app.jar"]
