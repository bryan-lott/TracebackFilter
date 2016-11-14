FROM openjdk
MAINTAINER Bryan Lott <lott.bryan@gmail.com>

RUN apt-get update

COPY ./target/uberjar/tracebackfilter-0.1.0-SNAPSHOT-standalone.jar /code/tracebackfilter.jar

WORKDIR /code

ENTRYPOINT ["java", "-jar", "/code/tracebackfilter.jar"]
CMD []
