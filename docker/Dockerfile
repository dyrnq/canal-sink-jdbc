ARG BASE_IMAGE

FROM ${BASE_IMAGE:-eclipse-temurin:21.0.4_7-jre-noble}
ARG JAR_FILE=target/*.jar
WORKDIR /app
COPY docker/docker-entrypoint.sh /usr/local/bin/
COPY ${JAR_FILE} /app/
RUN ln -s /usr/local/bin/docker-entrypoint.sh /docker-entrypoint.sh
ENTRYPOINT ["/docker-entrypoint.sh"]