FROM gradle:6.3-jdk11 AS build
WORKDIR /usr/src/build
COPY --chown=gradle:gradle . /usr/src/build/
RUN gradle :sensor-controller:build :sensor-controller:bootJar --no-daemon

FROM openjdk:11-jre
WORKDIR /usr/src/app
COPY --from=build /usr/src/build/sensor-controller/build/libs/*.jar application.jar
CMD ["java", "-jar","/usr/src/app/application.jar"]