FROM gradle:7.4-jdk-alpine

ENV APP_HOME=/var/app/

WORKDIR $APP_HOME

COPY build.gradle.kts settings.gradle.kts gradle.properties $APP_HOME
COPY src/ $APP_HOME/src/

RUN gradle --no-daemon clean shadowJar

FROM eclipse-temurin:17-jre-alpine
ENV APP_HOME=/var/app

WORKDIR $APP_HOME

RUN mkdir ${APP_HOME}/config && mkdir ${APP_HOME}/data

COPY --from=0 /var/app/build/libs/patreon-availability-bot.jar $APP_HOME
COPY start.sh $APP_HOME

RUN chmod u+x start.sh

CMD ["sh", "start.sh"]