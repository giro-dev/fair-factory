CREATE TABLE assistant_agent_config (
    agent_id    VARCHAR(64) NOT NULL PRIMARY KEY,
    enabled     BOOLEAN NOT NULL,
    provider    VARCHAR(32),
    model       VARCHAR(128),
    temperature DOUBLE PRECISION,
    max_tokens  INTEGER,
    updated_at  TIMESTAMP NOT NULL,
    updated_by  VARCHAR(128)
);
