INSERT INTO `emulator_settings` (`key`, `value`) VALUES
    ('wired.max_usage', '8750'),
    ('hotel.room.wallfurni.max', '2500'),
    ('hotel.room.furni.variable.max', '100'),
    ('hotel.room.user.variable.max', '100'),
    ('hotel.room.global.variable.max', '100')
ON DUPLICATE KEY UPDATE `value` = `value`;

INSERT INTO `emulator_texts` (`key`, `value`) VALUES
    ('commands.keys.cmd_wired_creator_tools', 'wired'),
    ('commands.description.cmd_wired_creator_tools', ':wired')
ON DUPLICATE KEY UPDATE `value` = `value`;
