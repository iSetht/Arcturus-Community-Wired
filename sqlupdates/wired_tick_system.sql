-- =====================================================
-- Wired Tick System - Configuration Settings
-- =====================================================
-- This SQL script adds the configuration options for the new 
-- high-resolution wired tick system (50ms default).
-- 
-- Run this script to configure the wired timer triggers.
-- =====================================================

-- Insert new wired tick system configuration settings
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.tick.interval.ms', '50'),
('wired.tick.debug', '0'),
('wired.tick.thread.priority', '6')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- =====================================================
-- Configuration Options Explained:
-- =====================================================
-- 
-- wired.tick.interval.ms
--   The tick interval in milliseconds for wired timer triggers.
--   Lower values = more precise timing but higher CPU usage.
--   Recommended: 50 (default), Range: 10-500
--   Default: 50
--
-- wired.tick.debug
--   Enable verbose debug logging for wired tick operations.
--   Logs each tick cycle and trigger execution.
--   Warning: Very verbose, only enable for troubleshooting.
--   Default: 0 (disabled)
--
-- wired.tick.thread.priority
--   Thread priority for the wired tick service (1-10).
--   Higher priority = better timing accuracy under load.
--   Java Thread priorities: MIN=1, NORM=5, MAX=10
--   Default: 6 (slightly above normal)
--
-- =====================================================
-- Usage Examples:
-- =====================================================
-- 
-- Increase tick resolution for competitive mini-games:
--   UPDATE emulator_settings SET value = '25' WHERE `key` = 'wired.tick.interval.ms';
--
-- Reduce CPU usage on low-end servers:
--   UPDATE emulator_settings SET value = '100' WHERE `key` = 'wired.tick.interval.ms';
--
-- Enable debug logging for troubleshooting:
--   UPDATE emulator_settings SET value = '1' WHERE `key` = 'wired.tick.debug';
--
-- =====================================================
