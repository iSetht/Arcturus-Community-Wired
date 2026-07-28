INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('wired.broadcast.max_depth', '10')
ON DUPLICATE KEY UPDATE `value` = `value`;

