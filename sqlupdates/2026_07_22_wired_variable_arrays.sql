-- Pass 1: Wired array definition/value foundation. Apply before deploying the matching backend.

ALTER TABLE `wired_variables`
    ADD COLUMN `value_shape` tinyint(3) NOT NULL DEFAULT 0 AFTER `value`,
    ADD COLUMN `array_length` int(11) NOT NULL DEFAULT 0 AFTER `value_shape`,
    ADD COLUMN `array_version` int(11) NOT NULL DEFAULT 0 AFTER `array_length`,
    ADD CONSTRAINT `chk_wired_variable_value_shape` CHECK (`value_shape` IN (0, 1)),
    ADD CONSTRAINT `chk_wired_variable_array_length` CHECK (`array_length` BETWEEN 0 AND 2048);

CREATE TABLE `wired_variable_array_values` (
    `variable_item_id` int(11) NOT NULL,
    `owner_type` int(11) NOT NULL,
    `owner_id` int(11) NOT NULL,
    `entry_index` int(11) NOT NULL,
    `field_id` int(11) NOT NULL,
    `value` bigint(20) NOT NULL,
    `created_at` bigint(20) NOT NULL DEFAULT 0,
    `updated_at` bigint(20) NOT NULL DEFAULT 0,
    PRIMARY KEY (`variable_item_id`, `owner_type`, `owner_id`, `entry_index`, `field_id`),
    CONSTRAINT `fk_wired_array_value_header`
        FOREIGN KEY (`variable_item_id`, `owner_type`, `owner_id`)
        REFERENCES `wired_variables` (`item_id`, `owner_type`, `owner_id`)
        ON DELETE CASCADE,
    CONSTRAINT `chk_wired_array_entry_index` CHECK (`entry_index` BETWEEN 0 AND 2047),
    CONSTRAINT `chk_wired_array_field_id` CHECK (`field_id` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('hotel.wired.variables.arrays.default_max_entries', '64'),
    ('hotel.wired.variables.arrays.max_entries', '2048'),
    ('hotel.wired.variables.arrays.max_populated_cells_per_owner', '4096'),
    ('hotel.wired.variables.arrays.permanent_owner_cache_limit', '128'),
    ('hotel.wired.variables.arrays.permanent_owner_cache_cell_limit', '65536'),
    ('hotel.wired.variables.arrays.max_owners_per_mutation', '50'),
    ('hotel.wired.variables.arrays.max_persistent_rows_per_mutation', '8192'),
    ('hotel.wired.variables.arrays.max_permanent_cells_per_room', '2000000'),
    ('hotel.wired.variables.arrays.max_permanent_cells_per_owner_in_room', '131072'),
    ('hotel.wired.variables.arrays.usage_entries_per_unit', '16'),
    ('hotel.wired.variables.arrays.usage_permanent_rows_per_unit', '8'),
    ('hotel.wired.variables.arrays.slow_persistence_ms', '50'),
    ('hotel.wired.variables.arrays.metrics_log_interval_ms', '60000')
ON DUPLICATE KEY UPDATE `value` = `value`;
