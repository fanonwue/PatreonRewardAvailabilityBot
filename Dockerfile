FROM gradle:8.8-jdk21-alpine

ENV APP_HOME=/var/app/

WORKDIR $APP_HOME

COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY src/ src/

RUN gradle --no-daemon clean build

FROM eclipse-temurin:21-jre-alpine
ENV APP_HOME=/var/app
WORKDIR $APP_HOME

RUN mkdir config && touch config/config.ini  \
  && mkdir data

COPY --from=0 /var/app/build/output/ .

ENTRYPOINT ["java", "-jar", "patreon-availability-bot.jar"]