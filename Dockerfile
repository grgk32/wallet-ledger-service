FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
COPY core/pom.xml core/pom.xml
COPY usecases/pom.xml usecases/pom.xml
COPY adapters/pom.xml adapters/pom.xml
COPY app/pom.xml app/pom.xml

RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY core/src core/src
COPY usecases/src usecases/src
COPY adapters/src adapters/src
COPY app/src app/src

RUN mvn -B -ntp -DskipTests package


FROM eclipse-temurin:21-jre AS runtime

ARG APPLICATION_HOME=/opt/wallet-ledger
ARG APPLICATION_USER=ledger
ARG APPLICATION_UID=10001

LABEL org.opencontainers.image.title="wallet-ledger-service" \
      org.opencontainers.image.description="Double-entry wallet ledger service" \
      org.opencontainers.image.version="1.0.0"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid ${APPLICATION_UID} ${APPLICATION_USER} \
    && useradd --uid ${APPLICATION_UID} --gid ${APPLICATION_UID} --shell /usr/sbin/nologin \
       --home-dir ${APPLICATION_HOME} --no-create-home ${APPLICATION_USER} \
    && mkdir --parents ${APPLICATION_HOME}

WORKDIR ${APPLICATION_HOME}

COPY --from=build --chown=${APPLICATION_UID}:${APPLICATION_UID} \
     /workspace/app/target/wallet-ledger-service.jar ${APPLICATION_HOME}/wallet-ledger-service.jar

USER ${APPLICATION_USER}

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

STOPSIGNAL SIGTERM

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 \
    CMD curl --fail --silent --output /dev/null http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar /opt/wallet-ledger/wallet-ledger-service.jar"]
