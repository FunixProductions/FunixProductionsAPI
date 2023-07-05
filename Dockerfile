FROM openjdk:17
ARG service_name
ARG service_base_dir
ENV SERVICE_NAME=${service_name}
ENV SERVICE_BASE_DIR=${service_base_dir}
ENV APP_VERSION=1.1.0

WORKDIR /container/java

ADD ./modules/${SERVICE_BASE_DIR}/service/target/funixproductions-${SERVICE_NAME}-service-${APP_VERSION}.jar /container/java/service.jar

WORKDIR /container/app
ENTRYPOINT ["/bin/sh", "-c", "java -jar -Xms300M -XX:MaxRAMPercentage=95.0 /container/java/service.jar"]
