#!/bin/bash

echo "DEBUG: MONGODB_PORT is $MONGODB_PORT"
echo "DEBUG: MONGO_SHARD_PORT is $MONGO_SHARD_PORT"

wait_for_port() {
  echo "Ожидание $1:$2..."
  until mongosh --host "$1:$2" --quiet --eval "print('connected')" >/dev/null 2>&1; do
    echo "Сервис $1 недоступен..."
    sleep 2
  done
}

echo "Starting initialization..."

# Используем переменные окружения, переданные через docker-compose
# Убедись, что MONGODB_PORT и MONGO_SHARD_PORT заданы в .env.local
CFG_PORT=${MONGODB_PORT}
SHD_PORT=${MONGO_SHARD_PORT}

# 1. Инициализация Config Server
wait_for_port configsvr01 ${CFG_PORT}
mongosh --host configsvr01:${CFG_PORT} --eval "rs.initiate({_id: 'configReplSet', configsvr: true, members: [{_id: 0, host: 'configsvr01:${CFG_PORT}'}]})"

# 2. Инициализация Shard 01
wait_for_port shard01-a ${SHD_PORT}
mongosh --host shard01-a:${SHD_PORT} --eval "rs.initiate({_id: 'shard01RS', members: [{_id: 0, host: 'shard01-a:${SHD_PORT}'}, {_id: 1, host: 'shard01-b:${SHD_PORT}'}, {_id: 2, host: 'shard01-c:${SHD_PORT}'}]})"

# 3. Инициализация Shard 02
wait_for_port shard02-a ${SHD_PORT}
mongosh --host shard02-a:${SHD_PORT} --eval "rs.initiate({_id: 'shard02RS', members: [{_id: 0, host: 'shard02-a:${SHD_PORT}'}, {_id: 1, host: 'shard02-b:${SHD_PORT}'}, {_id: 2, host: 'shard02-c:${SHD_PORT}'}]})"

# 4. Настройка кластера через Mongos
wait_for_port mongos ${CFG_PORT}

echo "Adding shards to cluster..."
mongosh --host mongos:${CFG_PORT} --eval "
  sh.addShard('shard01RS/shard01-a:${SHD_PORT}');
  sh.addShard('shard02RS/shard02-a:${SHD_PORT}');

  sh.enableSharding('eventhub');
  sh.shardCollection('eventhub.events', { 'created_by': 'hashed' });
"

echo "Creating application user..."
mongosh --host mongos:${CFG_PORT} --eval "
  db = db.getSiblingDB('eventhub');
  db.createUser({
    user: 'eventhub',
    pwd: 'eventhub',
    roles: [
      { role: 'readWrite', db: 'eventhub' },
      { role: 'dbAdmin', db: 'eventhub' },
      { role: 'clusterMonitor', db: 'admin' }
    ]
  });
"
echo "Mongos configured successfully"