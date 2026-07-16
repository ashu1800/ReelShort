-- V16: Add fair mode config for precise point calculation

INSERT INTO system_configs (config_key, config_value, description, updated_at)
VALUES ('points.fair-mode.enabled', '1', 'Fair mode: points calculated with 1 decimal precision internally (1=on, 0=off)', now());
