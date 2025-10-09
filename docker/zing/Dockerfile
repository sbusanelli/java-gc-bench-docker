# NOTE: This is a placeholder Dockerfile for Azul Platform Prime (C4).
# You must replace the FROM image below with the correct Azul Prime base image
# per your license/subscription (consult Azul docs). The rest should just work.

# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage (REPLACE THIS IMAGE with Azul Prime's Java 21+ image)
FROM azul/prime-base-image-placeholder:latest
WORKDIR /app
COPY --from=build /src/target/gc-bench-1.0-jar-with-dependencies.jar /app/app.jar
VOLUME ["/logs"]
ENV HEAP="" WORK=""
# Unified logging syntax is supported on modern Azul Prime builds
CMD bash -lc 'java $HEAP -Xlog:gc*,safepoint:file=/logs/gc-zing.log:time,uptime,level,tags $WORK -jar /app/app.jar'
