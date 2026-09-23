ALTER TABLE member ADD COLUMN oidc_subject VARCHAR(255);
CREATE UNIQUE INDEX idx_member_oidc_subject ON member(oidc_subject);
