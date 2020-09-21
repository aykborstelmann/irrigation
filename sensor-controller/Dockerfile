FROM gradle:6.6-jdk11 AS build
RUN mkdir -p /home/gradle/cache_home
WORKDIR /home/gradle/app
COPY build.gradle settings.gradle buildSrc/ /home/gradle/app/
COPY */build.gradle /home/gradle/app/*/
RUN gradle clean build -x test -i --stacktrace
COPY . /home/gradle/app
RUN gradle clean :sensor-controller:build :sensor-controller:bootJar

FROM adoptopenjdk:11-jre-hotspot
WORKDIR /usr/src/app
COPY --from=build /home/gradle/app/sensor-controller/build/libs/*.jar application.jar
CMD ["java", "-jar","/usr/src/app/application.jar"]