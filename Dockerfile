# https://github.com/folio-org/folio-tools/tree/master/folio-java-docker/openjdk17
FROM folioci/alpine-jre-openjdk17:latest
WORKDIR /app
# Install latest patch versions of packages: https://pythonspeed.com/articles/security-updates-in-docker/
USER root
RUN apk upgrade --no-cache
USER folio
EXPOSE 8081



# Устанавливаем бинарник как точку входа
ENTRYPOINT ["/app/myapp"]
