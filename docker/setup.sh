#!/bin/bash
until cqlsh ${CASSANDRA_HOSTS} -e "DESCRIBE KEYSPACES"; do
  echo "Cassandra is unavailable - sleeping"
  sleep 5
done

export CLEAN_KS=$(echo "${CASSANDRA_KEYSPACE}" | tr -d '"')
envsubst < /init.cql > /tmp/init_ready.cql
cqlsh ${CASSANDRA_HOSTS} -f /tmp/init_ready.cql