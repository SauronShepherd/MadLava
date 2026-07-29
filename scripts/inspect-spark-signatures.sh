#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 '<spark-jars-classpath>'" >&2
  exit 64
fi

javap -classpath "$1" -p -s \
  org.apache.spark.serializer.JavaSerializer \
  org.apache.spark.serializer.JavaSerializerInstance \
  org.apache.spark.serializer.JavaSerializationStream \
  org.apache.spark.serializer.JavaDeserializationStream \
  org.apache.spark.serializer.KryoSerializer \
  org.apache.spark.serializer.KryoSerializerInstance \
  org.apache.spark.serializer.KryoSerializationStream \
  org.apache.spark.serializer.KryoDeserializationStream
