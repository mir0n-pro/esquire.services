FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Create a non-root user for security
RUN useradd -ms /bin/bash springuser
USER springuser

# Copy the JAR file that was built locally on your machine into the container
# This assumes you ran 'mvn clean package' before running 'docker compose build'
COPY target/esquire-mesnie.jar app.jar

# Define how to start the application
#ENTRYPOINT ["java", "-jar", "app.jar"]
