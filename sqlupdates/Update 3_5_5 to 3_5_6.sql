-- SQL Update for FillRoomCommand
-- This adds the necessary text entries and permission for the :fillroom command

-- Add command key and description texts
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.keys.cmd_fillroom', 'fillroom;fill');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.description.cmd_fillroom', ':fillroom [count] [item_name] - Fill the room with random furniture items');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.no_room', 'You must be in a room to use this command.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.not_owner', 'You must be the room owner to use this command.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.room_full', 'The room has reached the maximum furniture limit.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.item_not_found', 'Item %item% was not found.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.not_floor_item', 'Only floor items can be placed with this command.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.no_items', 'No suitable items found to place.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.no_layout', 'Room layout not found.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.error.cmd_fillroom.no_tiles', 'No valid tiles found for placement.');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.info.cmd_fillroom.starting', 'Starting to fill room with %count% items...');
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('commands.succes.cmd_fillroom', 'Successfully placed %count%/%total% items in the room.');

-- Add permission for the command
ALTER TABLE `permissions` ADD `cmd_fillroom` ENUM('0', '1') NOT NULL DEFAULT '0';
ALTER TABLE `permissions` ADD `cmd_fillroom_any` ENUM('0', '1') NOT NULL DEFAULT '0';
