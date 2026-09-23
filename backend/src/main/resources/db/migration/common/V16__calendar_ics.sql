CREATE TABLE calendar_subscription (
    id CHAR(36) NOT NULL PRIMARY KEY,
    household_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    color VARCHAR(16),
    last_synced_at TIMESTAMP,
    last_error VARCHAR(1024),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_calendar_subscription_household ON calendar_subscription(household_id);

ALTER TABLE calendar_event ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE calendar_event ADD COLUMN subscription_id CHAR(36);
ALTER TABLE calendar_event ADD COLUMN external_uid VARCHAR(255);

CREATE INDEX idx_calendar_event_subscription ON calendar_event(subscription_id);
