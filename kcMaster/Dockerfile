
FROM eclipse-temurin:25-jre-jammy

#RUN mkdir -p /app/logs && chmod 777 /app/logs
WORKDIR /app


RUN useradd -ms /bin/bash springuser
USER springuser

COPY target/*.jar app.jar

# Define how to start the application
#ENTRYPOINT ["java", "-jar", "app.jar"]
