FROM openjdk:8-jdk-alpine 
RUN apk --no-cache add curl
COPY build/libs/*.jar basket-demo.jar
CMD java ${JAVA_OPTS} -jar basket-demo.jar