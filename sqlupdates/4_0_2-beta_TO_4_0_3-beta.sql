SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- Update 4.0.2-beta to 4.0.3-beta
-- =====================================================

-- =====================================================
-- SECTION 1: Battle Banzai Rate Limiting Settings
-- =====================================================
-- These settings prevent memory exhaustion attacks via
-- rapid wired triggering of Battle Banzai tile locks.

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
-- Maximum pending flood-fill tasks in the executor queue
-- Prevents memory exhaustion from rapid tile locking
('hotel.banzai.fill.max_queue', '50'),

-- Minimum interval (ms) between flood-fill calculations per game
-- Prevents abuse via rapid wired triggering
('hotel.banzai.fill.cooldown_ms', '100')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

SET FOREIGN_KEY_CHECKS = 1;
