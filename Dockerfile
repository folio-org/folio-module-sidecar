# https://github.com/folio-org/folio-tools/tree/master/folio-java-docker/openjdk17
FROM folioci/alpine-jre-openjdk17:latest
WORKDIR /app
# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
COPY target/my-binary-sidecar /app/myapp
RUN chmod +x /app/myapp
USER folio
EXPOSE 8081


ENTRYPOINT ["/app/myapp"]
