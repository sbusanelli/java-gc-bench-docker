# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /src/target/gc-bench-1.0-jar-with-dependencies.jar /app/app.jar
# Logs go to /logs (bind-mounted by docker-compose)
VOLUME ["/logs"]
ENV HEAP="" WORK=""
CMD bash -lc 'java $HEAP -XX:+UseZGC -Xlog:gc*,safepoint:file=/logs/gc-zgc.log:time,uptime,level,tags $WORK -jar /app/app.jar'
