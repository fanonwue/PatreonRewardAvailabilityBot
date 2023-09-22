FROM gradle:8.3-jdk-alpine

ENV APP_HOME=/var/app/

WORKDIR $APP_HOME

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src/ src/

RUN gradle --no-daemon clean shadowJar

FROM eclipse-temurin:17-jre-alpine
ENV APP_HOME=/var/app
WORKDIR $APP_HOME

RUN mkdir config && touch config/config.ini  \
  && mkdir data

COPY --from=0 /var/app/build/libs/patreon-availability-bot.jar .

ENTRYPOINT ["java", "-jar", "patreon-availability-bot.jar"]