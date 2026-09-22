CREATE TABLE automation_rule (
    id CHAR(36) NOT NULL PRIMARY KEY,
    household_id CHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL,
    day_of_week INTEGER NOT NULL,
    hour INTEGER NOT NULL,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_automation_rule_household ON automation_rule(household_id);
