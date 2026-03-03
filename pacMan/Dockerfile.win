FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN useradd -ms /bin/bash springuser
USER springuser

COPY target/*.jar app.jar
