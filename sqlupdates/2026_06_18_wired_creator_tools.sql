CREATE TABLE IF NOT EXISTS `wired_tools` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `page_id` TINYINT(3) UNSIGNED NOT NULL,
  `order_number` INT(11) NOT NULL DEFAULT 0,
  `catalog_name` VARCHAR(100) NOT NULL,
  `display_name` VARCHAR(100) NOT NULL DEFAULT '',
  `preview_asset` VARCHAR(255) NOT NULL DEFAULT '',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_wired_tools_page_order` (`page_id`, `order_number`, `id`),
  KEY `idx_wired_tools_catalog_name` (`catalog_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- page_id values:
-- 1 = Triggers
-- 2 = Effects
-- 3 = Conditions
-- 4 = Selectors
-- 5 = Add-Ons
-- 6 = Variables
-- 7 = Extras

-- Optional after your code deploy, if the column still exists:
-- ALTER TABLE `catalog_pages` DROP COLUMN `catalog_type`;
