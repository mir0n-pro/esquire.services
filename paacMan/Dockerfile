FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

RUN useradd -ms /bin/bash springuser
USER springuser

COPY target/*.jar app.jar
