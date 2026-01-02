-- Wired Abuse Protection Settings
-- These settings control the wired abuse detection and rate limiting system

-- Maximum recursion depth to prevent infinite loops (e.g., collision + chase triggering each other)
INSERT INTO `emulator_settings` (`key`, `value`) VALUES ('wired.abuse.max.recursion.depth', '10');

-- Maximum events of same type per room within the rate limit window before triggering a ban
-- Set higher for rooms with many users and complex wired setups
INSERT INTO `emulator_settings` (`key`, `value`) VALUES ('wired.abuse.max.events.per.window', '100');

-- Time window in milliseconds for counting rapid events
-- Events are counted within this window to detect abuse patterns
INSERT INTO `emulator_settings` (`key`, `value`) VALUES ('wired.abuse.rate.limit.window.ms', '10000');

-- Duration in milliseconds to ban wired execution in a room after abuse is detected
-- Default: 600000 (10 minutes)
INSERT INTO `emulator_settings` (`key`, `value`) VALUES ('wired.abuse.ban.duration.ms', '600000');

-- Wired Abuse Alert Texts
-- Alert shown to all users in the room when wired is temporarily disabled
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('wired.abuse.room.alert', 'Wired execution has been temporarily disabled in this room due to abuse detection. It will resume in %minutes% minutes.');

-- Title for the staff bubble alert
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('wired.abuse.staff.title', 'Wired Abuse Detected');

-- Message for the staff bubble alert
-- Available placeholders: %roomname%, %owner%, %minutes%
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('wired.abuse.staff.message', 'Room: %roomname%\nOwner: %owner%\nBanned for %minutes% minutes.');

-- Link text for the staff bubble alert (navigates to the room)
INSERT INTO `emulator_texts` (`key`, `value`) VALUES ('wired.abuse.staff.link', 'Go to Room');
