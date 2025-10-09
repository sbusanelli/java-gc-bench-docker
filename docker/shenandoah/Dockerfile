# Build stage (reuse the same builder)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Runtime stage: Red Hat OpenJDK 21 with Shenandoah available
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime
WORKDIR /app
COPY --from=build /src/target/gc-bench-1.0-jar-with-dependencies.jar /app/app.jar
VOLUME ["/logs"]
ENV HEAP="" WORK=""
CMD bash -lc 'java $HEAP -XX:+UseShenandoahGC -Xlog:gc*,safepoint:file=/logs/gc-shenandoah.log:time,uptime,level,tags $WORK -jar /app/app.jar'
