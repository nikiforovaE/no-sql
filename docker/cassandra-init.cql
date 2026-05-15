CREATE KEYSPACE IF NOT EXISTS ${CASSANDRA_KEYSPACE}
    WITH REPLICATION = {
        'class' : 'NetworkTopologyStrategy',
        'dc1' : 1
        };

USE ${CASSANDRA_KEYSPACE};

CREATE TABLE IF NOT EXISTS event_reactions
(
    event_id   text,
    created_by text,
    like_value tinyint, -- 1 или -1
    created_at timestamp,
    PRIMARY KEY (event_id, created_by)
);

CREATE INDEX IF NOT EXISTS ON event_reactions (like_value);
CREATE INDEX IF NOT EXISTS ON event_reactions (created_by);