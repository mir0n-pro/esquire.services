FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Create a non-root user for security
RUN useradd -ms /bin/bash springuser
USER springuser

# The executable jar built by 'mvn package' -- named explicitly, because this module also attaches
# nothing else to target/ and a wildcard would break the moment it did.
COPY target/esquire-gate-ward.jar app.jar
