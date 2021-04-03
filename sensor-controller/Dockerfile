FROM adoptopenjdk:11-jre-hotspot-bionic
WORKDIR /usr/src/app
COPY build/libs/sensor-controller.jar application.jar
CMD ["java", "-jar","/usr/src/app/application.jar"]