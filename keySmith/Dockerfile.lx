# Multi-stage build — build context: esquire.services/
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*
COPY pom.xml .
COPY common/ common/
COPY bizTree/ bizTree/
COPY gateway/ gateway/
COPY enyMan/ enyMan/
COPY pacMan/ pacMan/
COPY keySmith/ keySmith/
RUN mvn clean package -pl common,keySmith -am -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd -ms /bin/bash springuser
USER springuser
COPY --from=builder /build/keySmith/target/*.jar app.jar
