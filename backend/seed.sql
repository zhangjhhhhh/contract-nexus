-- ============================================================
-- 合同管理系统 · 初始数据
-- 用法：先执行 schema.sql，再执行本文件。
-- 角色：admin / operator / new_user
-- 账号：admin/admin123，operator/operator123，newuser/newuser123
-- （与前端 README 测试账号一致；密码为明文，演示用）
-- ============================================================

USE contract_system;

-- 角色（与后端 ensureDefaultRoles 保持一致，后端启动时也会补建）
INSERT INTO `role` (`name`, `description`, `functions`) VALUES
  ('admin', '合同管理员', 'dashboard:view,contract:draft,contract:countersign,contract:finalize,contract:approve,contract:sign,query:info,query:process,base:contract,base:customer,system:assign,system:user,system:role,system:permission,system:log'),
  ('operator', '合同操作员', 'dashboard:view,contract:draft,contract:countersign,contract:finalize,contract:approve,contract:sign,query:info,query:process,base:contract,base:customer'),
  ('new_user', '新用户', '')
ON DUPLICATE KEY UPDATE
  `description` = VALUES(`description`),
  `functions`   = VALUES(`functions`);

-- 初始用户
INSERT INTO `user` (`userName`, `password`, `email`) VALUES
  ('admin',    'admin123',    'user@example.com'),
  ('operator', 'operator123', 'user@example.com'),
  ('newuser',  'newuser123',  'user@example.com')
ON DUPLICATE KEY UPDATE
  `password` = VALUES(`password`),
  `email`    = VALUES(`email`);

-- 用户-角色关联（先清掉这三个账号的旧映射再插入，保证可重复执行）
DELETE FROM `right` WHERE `userName` IN ('admin', 'operator', 'newuser');
INSERT INTO `right` (`userName`, `roleName`, `description`) VALUES
  ('admin',    'admin',    ''),
  ('operator', 'operator', ''),
  ('newuser',  'new_user', '');
