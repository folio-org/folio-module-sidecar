# https://github.com/folio-org/folio-tools/tree/master/folio-java-docker/openjdk21
FROM folioci/alpine-jre-openjdk21:latest

# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY target/quarkus-app/lib/ ${JAVA_APP_DIR}/lib/
COPY target/quarkus-app/*.jar ${JAVA_APP_DIR}/
COPY target/quarkus-app/app/ ${JAVA_APP_DIR}/app/
COPY target/quarkus-app/quarkus/ ${JAVA_APP_DIR}/quarkus/

ENV JAVA_APP_JAR="${JAVA_APP_DIR}/quarkus-run.jar"

# Expose this port locally in the container.
EXPOSE 8081

ENTRYPOINT [ "./run-java.sh", "-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" ]
