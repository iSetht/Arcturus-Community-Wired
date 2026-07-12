CREATE TABLE IF NOT EXISTS `wired_variables` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `item_id` int(11) NOT NULL,
  `room_id` int(11) NOT NULL,
  `variable_type` int(11) NOT NULL,
  `variable_name` varchar(40) NOT NULL,
  `persistence` int(11) NOT NULL DEFAULT 0,
  `owner_type` int(11) NOT NULL DEFAULT 0,
  `owner_id` int(11) NOT NULL DEFAULT 0,
  `value` bigint(20) NOT NULL DEFAULT 0,
  `created_at` bigint(20) NOT NULL DEFAULT 0,
  `updated_at` bigint(20) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_wired_variable_owner` (`item_id`, `owner_type`, `owner_id`),
  KEY `idx_wired_variables_room_name` (`room_id`, `variable_type`, `variable_name`),
  KEY `idx_wired_variables_shared` (`variable_type`, `variable_name`, `persistence`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
