#!/bin/bash

# Example usage:
#
#   APPLICATION_ENVIRONMENT=prepdev rpki-ripe-ncc.sh
#

JAVA_HOME=${JAVA_HOME:-"/usr/lib/jvm/jre-17-openjdk"}
LANG=${LANG:-"en_US.UTF-8"}

cd "$(dirname "$0")" || exit 1

export JAVA_HOME
export LANG

CORE_JAR=${CORE_JAR:-"./rpki-ripe-ncc.jar"}

CORE_OPTS=(
    "--spring.profiles.active=$APPLICATION_ENVIRONMENT"
    "--spring.config.additional-location=${SPRING_CONFIG_ADDITIONAL_LOCATION:-file:/cert/shared/rpki-config-credentials.properties}"
)

case "$APPLICATION_ENVIRONMENT" in
    production)
        ENV_OPTS=("-Xms8g" "-Xmx8g")
        HSM_OPTS=("-Dprotect=module" "-DignorePassphrase=true")
        LOG_DIR="../logs"
        ;;
    prepdev)
        ENV_OPTS=("-Xms8g" "-Xmx8g"
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
            "-javaagent:opentelemetry-javaagent-1.26.0.jar"
            "-Dotel.javaagent.configuration-file=/cert/shared/opentelemetry-java-agent.conf"
            "-Dotel.resource.attributes=service.name=rpki-core,deployment.environment=${APPLICATION_ENVIRONMENT}"
            )
        HSM_OPTS=("-Dprotect=module" "-DignorePassphrase=true")
        LOG_DIR="../logs"
        ;;
    pilot)
        ENV_OPTS=("-Xms512m" "-Xmx2048m")
        LOG_DIR="../logs"
        ;;
    local)
        CORE_OPTS=("--spring.profiles.active=$APPLICATION_ENVIRONMENT")
        LOG_DIR="."
        ;;
    *)
        echo "$0: unknown application environment '$APPLICATION_ENVIRONMENT', aborting" >&2
        exit 1
        ;;
esac

JAVA_OPTS=(
    "-DAPPLICATION_ENVIRONMENT=$APPLICATION_ENVIRONMENT"
    "-Dinstance.name=$(hostname)"
    "-XX:+UseParallelGC"
    "-Xlog:gc:$LOG_DIR/gc.log:utctime"
    "-XX:+HeapDumpOnOutOfMemoryError" "-XX:HeapDumpPath=$LOG_DIR" "-XX:+ExitOnOutOfMemoryError"
    "${ENV_OPTS[@]}"
    "${HSM_OPTS[@]}"
)

exec "$JAVA_HOME/bin/java" "${JAVA_OPTS[@]}" $EXTRA_JAVA_OPTS -jar "$CORE_JAR" "${CORE_OPTS[@]}" $EXTRA_CORE_OPTS
