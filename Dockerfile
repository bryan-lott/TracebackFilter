FROM openjdk
MAINTAINER Bryan Lott <lott.bryan@gmail.com>

RUN sudo apt-get update

COPY ./target/uberjar/tracebackfilter-0.1.0-SNAPSHOT-standalone.jar /code/tracebackfilter.jar

WORKDIR /code

CMD ["java","-jar","/code/tracebackfilter.jar"]
