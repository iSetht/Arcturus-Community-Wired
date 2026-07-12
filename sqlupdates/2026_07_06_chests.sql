CREATE TABLE IF NOT EXISTS `items_chest_storage` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `chest_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `deposited_by` int(11) NOT NULL DEFAULT 0,
  `deposited_at` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_chest_item` (`item_id`),
  KEY `chest_id` (`chest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `items_chest_coins` (
  `chest_id` int(11) NOT NULL,
  `coins` int(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`chest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `items_chest_settings` (
  `chest_id` int(11) NOT NULL,
  `allow_open` tinyint(1) NOT NULL DEFAULT 1,
  `allow_donate` tinyint(1) NOT NULL DEFAULT 0,
  `display_name` varchar(64) NOT NULL DEFAULT '',
  `description` varchar(255) NOT NULL DEFAULT '',
  `appearance_state` int(11) NOT NULL DEFAULT 0,
  `preview_mode` int(11) NOT NULL DEFAULT 0,
  `preview_amount` int(11) NOT NULL DEFAULT 1,
  `capacity` int(11) NOT NULL DEFAULT 0,
  `locked` tinyint(1) NOT NULL DEFAULT 1,
  `auto_lock` tinyint(1) NOT NULL DEFAULT 0,
  `notify_full` tinyint(1) NOT NULL DEFAULT 1,
  `notify_donation` tinyint(1) NOT NULL DEFAULT 1,
  `notify_withdraw` tinyint(1) NOT NULL DEFAULT 1,
  `notify_empty` tinyint(1) NOT NULL DEFAULT 1,
  `notify_wired_transaction` tinyint(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`chest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE `items_chest_settings`
  ADD COLUMN IF NOT EXISTS `locked` tinyint(1) NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS `auto_lock` tinyint(1) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS `notify_full` tinyint(1) NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS `notify_donation` tinyint(1) NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS `notify_withdraw` tinyint(1) NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS `notify_empty` tinyint(1) NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS `notify_wired_transaction` tinyint(1) NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS `wired_chest_logs` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `room_id` int(11) NOT NULL,
  `created_at` bigint(20) NOT NULL,
  `transaction_type` varchar(32) NOT NULL DEFAULT 'MANUAL',
  `user_id` int(11) NOT NULL DEFAULT 0,
  `username` varchar(64) NOT NULL DEFAULT '',
  `withdrawal_furni` int(11) NOT NULL DEFAULT 0,
  `withdrawal_coins` int(11) NOT NULL DEFAULT 0,
  `deposit_furni` int(11) NOT NULL DEFAULT 0,
  `deposit_coins` int(11) NOT NULL DEFAULT 0,
  `chest_count` int(11) NOT NULL DEFAULT 0,
  `details_json` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `wired_chest_logs_room_newest` (`room_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `emulator_settings` (`key`, `value`) VALUES
  ('wired.coin_chest.capacity', '250000'),
  ('wired.furni_chest.capacity', '1000')
ON DUPLICATE KEY UPDATE `value` = VALUES(`value`);

UPDATE `items_chest_settings` settings
INNER JOIN `items` item ON item.id = settings.chest_id
INNER JOIN `items_base` base ON base.id = item.item_id
SET settings.capacity = 1000
WHERE base.interaction_type = 'wf_storage_furni';
