-- ============================================================
-- 合同管理系统 · 数据库建表脚本
-- 目标库：contract_system（MySQL 5.7+ / 8.0，utf8mb4）
-- 注意：重新执行本脚本会 DROP 并重建所有表（清空数据）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS contract_system
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE contract_system;

-- 用户表
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`       INT AUTO_INCREMENT PRIMARY KEY,
  `userName` VARCHAR(50)  NOT NULL,
  `password` VARCHAR(64)  NOT NULL,
  `email`    VARCHAR(120) NULL,
  UNIQUE KEY `uk_user_name` (`userName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表（权限以逗号分隔的 key 存于 functions）
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
  `id`          INT AUTO_INCREMENT PRIMARY KEY,
  `name`        VARCHAR(50)  NOT NULL,
  `description` VARCHAR(255) NULL,
  `functions`   TEXT         NULL,
  UNIQUE KEY `uk_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-角色关联表（历史表名 right）
DROP TABLE IF EXISTS `right`;
CREATE TABLE `right` (
  `id`          INT AUTO_INCREMENT PRIMARY KEY,
  `userName`    VARCHAR(50)  NOT NULL,
  `roleName`    VARCHAR(50)  NOT NULL,
  `description` VARCHAR(255) NULL,
  UNIQUE KEY `uk_right` (`userName`, `roleName`),
  KEY `idx_right_role` (`roleName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 权限/功能表（可为空；权限直接以 key 存于 role.functions）
DROP TABLE IF EXISTS `function`;
CREATE TABLE `function` (
  `num`         VARCHAR(20)  PRIMARY KEY,
  `name`        VARCHAR(50)  NULL,
  `URL`         VARCHAR(255) NULL,
  `description` VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客户表
DROP TABLE IF EXISTS `customer`;
CREATE TABLE `customer` (
  `num`     VARCHAR(50)  PRIMARY KEY,
  `name`    VARCHAR(100) NOT NULL,
  `tel`     VARCHAR(30)  NULL,
  `address` VARCHAR(255) NULL,
  `fax`     VARCHAR(30)  NULL,
  `email`   VARCHAR(60)  NULL,
  `bank`    VARCHAR(100) NULL,
  `account` VARCHAR(100) NULL,
  `remark`  TEXT         NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 合同表
DROP TABLE IF EXISTS `contract`;
CREATE TABLE `contract` (
  `num`       VARCHAR(50) PRIMARY KEY,
  `name`      VARCHAR(100) NOT NULL,
  `customer`  VARCHAR(50)  NULL,
  `beginTime` DATE         NULL,
  `endTime`   DATE         NULL,
  `content`   LONGTEXT     NULL,
  `userName`  VARCHAR(50)  NULL,
  `aiReview`  LONGTEXT     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 合同状态表（type: 1起草 2定稿 3审批 4签订 5完成）
DROP TABLE IF EXISTS `contract_state`;
CREATE TABLE `contract_state` (
  `id`     INT AUTO_INCREMENT PRIMARY KEY,
  `conNum` VARCHAR(50) NOT NULL,
  `type`   INT         NOT NULL,
  `time`   DATETIME    NULL,
  UNIQUE KEY `uk_state` (`conNum`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 合同流程表（type: 1会签 2审批 3签订 4定稿；state: 0待办 1完成 2驳回）
DROP TABLE IF EXISTS `contract_process`;
CREATE TABLE `contract_process` (
  `id`               INT AUTO_INCREMENT PRIMARY KEY,
  `conNum`           VARCHAR(50)  NOT NULL,
  `type`             INT          NOT NULL,
  `state`            INT          NOT NULL,
  `userName`         VARCHAR(50)  NULL,
  `content`          TEXT         NULL,
  `time`             DATETIME     NULL,
  `createdAt`        DATETIME     NULL,
  `signerName`       VARCHAR(100) NULL,
  `signatureDataUrl` LONGTEXT     NULL,
  KEY `idx_process_con` (`conNum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 合同附件表
DROP TABLE IF EXISTS `contract_attachment`;
CREATE TABLE `contract_attachment` (
  `id`         INT AUTO_INCREMENT PRIMARY KEY,
  `conNum`     VARCHAR(50)  NOT NULL,
  `fileName`   VARCHAR(255) NOT NULL,
  `path`       VARCHAR(255) NOT NULL,
  `type`       VARCHAR(30)  NOT NULL,
  `uploadTime` DATETIME     NULL,
  KEY `idx_attachment_con` (`conNum`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文件存储表（上传文件正文）
DROP TABLE IF EXISTS `file_storage`;
CREATE TABLE `file_storage` (
  `storedName`   VARCHAR(255) PRIMARY KEY,
  `originalName` VARCHAR(255) NOT NULL,
  `type`         VARCHAR(30)  NOT NULL,
  `contentType`  VARCHAR(120) NOT NULL,
  `size`         BIGINT       NOT NULL,
  `content`      LONGBLOB     NOT NULL,
  `uploadTime`   DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 合同版本表
DROP TABLE IF EXISTS `contract_version`;
CREATE TABLE `contract_version` (
  `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
  `conNum`         VARCHAR(50)  NOT NULL,
  `versionNo`      INT          NOT NULL,
  `num`            VARCHAR(50)  NOT NULL,
  `name`           VARCHAR(100) NOT NULL,
  `customer`       VARCHAR(50)  NOT NULL,
  `beginTime`      DATE         NOT NULL,
  `endTime`        DATE         NOT NULL,
  `content`        TEXT         NOT NULL,
  `userName`       VARCHAR(50)  NOT NULL,
  `approverName`   VARCHAR(50)  NOT NULL,
  `approvalResult` VARCHAR(20)  NOT NULL,
  `approvalOpinion` TEXT        NOT NULL,
  `createdAt`      DATETIME     NOT NULL,
  UNIQUE KEY `uk_contract_version_no` (`conNum`, `versionNo`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 合同版本附件表
DROP TABLE IF EXISTS `contract_version_attachment`;
CREATE TABLE `contract_version_attachment` (
  `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
  `versionId`  BIGINT       NOT NULL,
  `fileName`   VARCHAR(255) NOT NULL,
  `path`       VARCHAR(255) NOT NULL,
  `type`       VARCHAR(30)  NOT NULL,
  `uploadTime` DATETIME     NULL,
  CONSTRAINT `fk_version_attachment` FOREIGN KEY (`versionId`)
    REFERENCES `contract_version` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
DROP TABLE IF EXISTS `log`;
CREATE TABLE `log` (
  `id`       BIGINT AUTO_INCREMENT PRIMARY KEY,
  `userName` VARCHAR(50)  NOT NULL,
  `content`  VARCHAR(255) NOT NULL,
  `time`     DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
