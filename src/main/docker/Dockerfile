FROM openjdk:8-jre-alpine

RUN mkdir -p /app
COPY app.jar /app/app.jar

EXPOSE 9889

ENTRYPOINT ["java", \
            "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", \
            "-Xmx24m", \
            "-jar", "/app/app.jar", \
           "--spring.config.additional-location=file:/data/config/" \
            ]