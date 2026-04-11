#!/bin/bash
wait_for_port() {
  while ! timeout 1s bash -c "cat < /dev/null > /dev/tcp/$1/$2" 2>/dev/null; do
    sleep 2
  done
}

echo "Starting initialization..."

# 1. Инициализация Config Server
wait_for_port configsvr01 27017
mongosh --host configsvr01:27017 --eval 'rs.initiate({_id: "configReplSet", configsvr: true, members: [{_id: 0, host: "configsvr01:27017"}]})'

# 2. Инициализация Shard 01 (3 ноды)
wait_for_port shard01-a 27018
mongosh --host shard01-a:27018 --eval 'rs.initiate({_id: "shard01RS", members: [{_id: 0, host: "shard01-a:27018"}, {_id: 1, host: "shard01-b:27018"}, {_id: 2, host: "shard01-c:27018"}]})'

# 3. Инициализация Shard 02 (3 ноды)
wait_for_port shard02-a 27018
mongosh --host shard02-a:27018 --eval 'rs.initiate({_id: "shard02RS", members: [{_id: 0, host: "shard02-a:27018"}, {_id: 1, host: "shard02-b:27018"}, {_id: 2, host: "shard02-c:27018"}]})'

# 4. Настройка кластера через Mongos
wait_for_port mongos 27017

echo "Adding shards to cluster..."
mongosh --host mongos:27017 --eval '
  sh.addShard("shard01RS/shard01-a:27018");
  sh.addShard("shard02RS/shard02-a:27018");

  sh.enableSharding("eventhub");

  // Шардирование коллекции events по хешу created_by
  sh.shardCollection("eventhub.events", { "created_by": "hashed" });
'
echo "Creating application user..."
mongosh --host mongos:27017 --eval '
  db = db.getSiblingDB("eventhub");
  db.createUser({
    user: "eventhub",
    pwd: "eventhub",
    roles: [
      { role: "readWrite", db: "eventhub" },
      { role: "dbAdmin", db: "eventhub" },
      { role: "clusterMonitor", db: "admin" }
    ]
  });
'
echo "Mongos configured"