CREATE TABLE `users` (
                         `id` bigint NOT NULL AUTO_INCREMENT,
                         `customer_key` varchar(255) DEFAULT NULL,
                         `email` varchar(255) NOT NULL,
                         `password` varchar(255) NOT NULL,
                         `phonenumber` varchar(255) DEFAULT NULL,
                         `username` varchar(255) NOT NULL,
                         `role` enum('ADMIN','DIRECTOR','USER') NOT NULL,
                         `status` enum('ACTIVE','DELETED') NOT NULL,
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `events` (
                          `end_date` date NOT NULL,
                          `running_time` int DEFAULT NULL,
                          `start_date` date NOT NULL,
                          `id` bigint NOT NULL AUTO_INCREMENT,
                          `banner_image_url` varchar(255) NOT NULL,
                          `category` varchar(255) DEFAULT NULL,
                          `description` varchar(255) DEFAULT NULL,
                          `description_image_url` varchar(255) DEFAULT NULL,
                          `location` varchar(255) NOT NULL,
                          `main_image_url` varchar(255) NOT NULL,
                          `name` varchar(255) NOT NULL,
                          PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=617
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `event_directors` (
                                   `event_id` bigint NOT NULL,
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `user_id` bigint NOT NULL,
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_event_director` (`event_id`, `user_id`),
                                   CONSTRAINT `FKo5sgrtonppbovvdosdf3if774`
                                       FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
                                   CONSTRAINT `FKphv132ir3mgfmlst3xt6skafk`
                                       FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tickets` (
                           `price` int DEFAULT NULL,
                           `sold_ticket` int DEFAULT NULL,
                           `total_ticket` int DEFAULT NULL,
                           `booking_endtime` datetime(6) DEFAULT NULL,
                           `event_id` bigint DEFAULT NULL,
                           `id` bigint NOT NULL AUTO_INCREMENT,
                           `start_time` datetime(6) DEFAULT NULL,
                           `description` varchar(255) DEFAULT NULL,
                           `type` varchar(255) NOT NULL,
                           PRIMARY KEY (`id`),
                           CONSTRAINT `FK3utafe14rupaypjocldjaj4ol`
                               FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=164
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ticket_price_history` (
                                        `id` bigint NOT NULL AUTO_INCREMENT,
                                        `ticket_id` bigint NOT NULL,
                                        `price` int NOT NULL,
                                        `changed_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                                        PRIMARY KEY (`id`),
                                        CONSTRAINT `fk_ticket_price_history_ticket`
                                            FOREIGN KEY (`ticket_id`) REFERENCES `tickets` (`id`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `orders` (
                          `quantity` int NOT NULL,
                          `event_id` bigint DEFAULT NULL,
                          `expires_at` datetime(6) DEFAULT NULL,
                          `id` bigint NOT NULL AUTO_INCREMENT,
                          `order_date` datetime(6) DEFAULT NULL,
                          `ticket_id` bigint DEFAULT NULL,
                          `user_id` bigint NOT NULL,
                          `order_id` varchar(64) NOT NULL,
                          `unit_price` int NOT NULL,
                          `total_amount` bigint NOT NULL,
                          `paid_at` datetime(6) DEFAULT NULL,
                          `idempotency_key` varchar(36) NOT NULL,
                          `payment_key` varchar(200) DEFAULT NULL,
                          `next_recovery_at` datetime(6) DEFAULT NULL,
                          `recovery_attempts` int NOT NULL DEFAULT 0,
                          `recovery_review_required` boolean NOT NULL DEFAULT FALSE,
                          `status` enum('CANCELED','CANCELING','CONFIRMING','EXPIRED','PAID','PAYMENT_FAILED','PENDING') DEFAULT NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `UKhmsk25beh6atojvle1xuymjj0` (`order_id`),
                          UNIQUE KEY `uk_orders_idempotency_key` (`idempotency_key`),
                          CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih`
                              FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payments` (
                            `id` bigint NOT NULL AUTO_INCREMENT,
                            `amount` bigint NOT NULL,
                            `approved_at` datetime(6) DEFAULT NULL,
                            `method` varchar(255) DEFAULT NULL,
                            `payment_key` varchar(200) NOT NULL,
                            `receipt_url` varchar(500) DEFAULT NULL,
                            `status` varchar(30) NOT NULL,
                            `order_id` bigint NOT NULL,
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `UK35yqdahtiysne6iij9ske72bj` (`payment_key`),
                            UNIQUE KEY `UK8vo36cen604as7etdfwmyjsxt` (`order_id`),
                            CONSTRAINT `FK81gagumt0r8y3rmudcgpbk42l`
                                FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6
  DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
