# Build environment for the Anthropic OSS Scanner (https://github.com/anthropics/oss-scanner).
# The scanner builds this file with the repository as context and then runs the image without network,
# so every Maven artifact needed to build, test and run GeoStore is downloaded here.
FROM maven:3.9-eclipse-temurin-17

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates curl git python3 procps \
    && rm -rf /var/lib/apt/lists/*

# scanner contract: the checkout lives inside the image, at /src
COPY . /src
WORKDIR /src

# Full build with the same profiles as CI. A failing test is reported but does not fail the image.
RUN mvn -B -ntp install -Ph2_disk,auditing -Dmaven.test.failure.ignore=true

# Webapp with the default in-memory H2 database, as the REST integration tests run it.
RUN mvn -B -ntp -pl src/web/app -am -DskipTests clean install

# Start the webapp once so the Jetty plugin and its dependencies are cached for offline use.
# Offline, run it again with:
#   cd /src/src/web/app && INITIAL_ADMIN_PASSWORD=<password> mvn -o jetty:run
# REST API: http://localhost:9191/geostore/rest/ (admin user created from INITIAL_ADMIN_PASSWORD on first start).
RUN cd src/web/app \
    && (INITIAL_ADMIN_PASSWORD=oss-scanner-warmup mvn -B -ntp jetty:run > /tmp/jetty-warmup.log 2>&1 &) \
    && ok=0; for i in $(seq 1 150); do \
         code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:9191/geostore/rest/users/user/details || true); \
         case "$code" in 200|401|403) ok=1; break;; esac; sleep 2; \
       done \
    ; pkill -f jetty || true; sleep 3 \
    ; if [ "$ok" != 1 ]; then tail -50 /tmp/jetty-warmup.log; echo "WARNING: GeoStore did not answer during warm-up"; fi \
    ; rm -f /tmp/jetty-warmup.log /src/src/web/app/*.mv.db

# Maven offline mode refuses artifacts whose _remote.repositories marker names a repository other than the
# one it would ask now. Without network every cached artifact is valid, so drop the markers.
RUN find /root/.m2/repository -name _remote.repositories -delete
