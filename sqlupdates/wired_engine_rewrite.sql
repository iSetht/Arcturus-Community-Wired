-- =====================================================
-- Wired Engine Rewrite - Configuration Settings
-- =====================================================
-- This SQL script adds the configuration options for the new 
-- context-driven wired engine architecture.
-- 
-- Run this script after upgrading to enable the new wired system.
-- =====================================================

-- Insert new wired engine configuration settings
INSERT INTO `emulator_settings` (`key`, `value`) VALUES
('wired.engine.enabled', '0'),
('wired.engine.exclusive', '0'),
('wired.engine.maxStepsPerStack', '100'),
('wired.engine.debug', '0')
ON DUPLICATE KEY UPDATE `key` = `key`;

-- =====================================================
-- Configuration Options Explained:
-- =====================================================
-- 
-- wired.engine.enabled
--   Enable the new wired engine. When set to 1, the new engine
--   runs alongside the legacy WiredHandler for parallel testing.
--   Default: 0 (disabled)
--
-- wired.engine.exclusive
--   When set to 1, disables the legacy WiredHandler completely.
--   Only enable this after thorough testing with parallel mode.
--   Default: 0 (legacy handler still active)
--
-- wired.engine.maxStepsPerStack
--   Maximum number of steps (trigger checks + condition evaluations
--   + effect executions) allowed per wired stack execution.
--   Prevents infinite loops from misconfigured wired setups.
--   Default: 100
--
-- wired.engine.debug
--   Enable verbose debug logging for wired execution.
--   Useful for troubleshooting wired stack behavior.
--   Default: 0 (disabled)
--
-- =====================================================
-- Migration Path:
-- =====================================================
-- 
-- Phase 1: Parallel Testing
--   UPDATE emulator_settings SET value = '1' WHERE `key` = 'wired.engine.enabled';
--   -- Test all wired functionality, compare behavior between old and new
--
-- Phase 2: Switch to New Engine
--   UPDATE emulator_settings SET value = '1' WHERE `key` = 'wired.engine.exclusive';
--   -- Legacy handler disabled, new engine handles all wired events
--
-- Phase 3: Cleanup (after confirming stability)
--   -- Remove legacy WiredHandler calls from codebase
--
-- =====================================================
