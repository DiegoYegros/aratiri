#!/bin/sh
set -e

exec java \
  --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
  --enable-native-access=ALL-UNNAMED \
  -javaagent:/opentelemetry-javaagent.jar \
  -jar /app.jar "$@"