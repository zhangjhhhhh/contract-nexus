# 合同管理系统后端

本目录是合同管理系统的 Spring Boot 后端服务。

## 运行

进入 `backend` 目录：

```powershell
mvn spring-boot:run
```

默认地址：

```text
http://127.0.0.1:8080
```

健康检查：

```text
GET http://127.0.0.1:8080/api/health
```

## 本地环境配置

后端启动前会自动读取 `.env`。推荐复制示例文件后，把本机数据库和百炼配置写入 `backend/.env`：

```powershell
cd backend
Copy-Item .env.example .env
```

`.env` 已被 Git 忽略，不会提交到仓库；仓库只提交 `.env.example` 这种占位模板。从仓库根目录启动时，后端也会自动尝试读取 `backend/.env`。

## 数据库配置

默认启用 `db` profile，需要在 `backend/.env` 中提供：

```text
DB_URL=jdbc:mysql://localhost:3306/contract_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password
```

首次初始化数据库（建库建表 + 初始账号）：

```powershell
mysql -u root -p < schema.sql
mysql -u root -p < seed.sql
```

> `schema.sql` 会创建 `contract_system` 库和全部表；`seed.sql` 写入 admin/operator/newuser 三个初始账号（密码见文件顶部注释）。重新执行 `schema.sql` 会清空并重建所有表。

临时使用内存仓库：

```powershell
$env:SPRING_PROFILES_ACTIVE="memory"
mvn spring-boot:run
```

## AI 审查配置

起草合同时，后端会读取已上传的 `docx/pdf` 附件，从文件中提取文字内容，然后把文字内容放入百炼应用调用的 `prompt`。不会再把原始文件上传给百炼，也不再需要阿里云 `AccessKey ID/Secret`。

需要的环境变量：

```text
DASHSCOPE_API_KEY=your_dashscope_api_key
BAILIAN_REVIEW_APP_ID=your_review_agent_app_id
BAILIAN_LEGAL_CHAT_APP_ID=your_legal_chat_agent_app_id
BAILIAN_APP_ID=your_legacy_default_app_id
AI_REVIEW_ENABLED=true
AI_REVIEW_TIMEOUT_SECONDS=120
```

`BAILIAN_REVIEW_APP_ID` 用于 AI 审查智能体，`BAILIAN_LEGAL_CHAT_APP_ID` 用于“四海”法律顾问聊天智能体。旧配置 `BAILIAN_APP_ID` 仍保留为兼容默认值。

Windows PowerShell 默认控制台编码通常是 GBK/936，后端默认按 `GBK` 输出控制台日志，避免 `[AI瀹℃煡]` 这类乱码：

```powershell
$env:LOGGING_CHARSET_CONSOLE="GBK"
```

如果你的终端已经切到 UTF-8，再改成：

```powershell
chcp 65001
$env:LOGGING_CHARSET_CONSOLE="UTF-8"
```

可选：限制单次提交给模型的附件文字长度，默认 `120000`：

```powershell
$env:AI_REVIEW_MAX_TEXT_CHARS="120000"
```

如果审查智能体返回较慢，可调大超时时间，默认 `120` 秒：

```powershell
$env:AI_REVIEW_TIMEOUT_SECONDS="120"
```

完整临时启动示例：

```powershell
cd backend
mvn.cmd spring-boot:run
```

排查时看后端控制台中的 `[AI审查]` 日志，正常链路依次会出现：

```text
[AI审查] 已提交异步审查任务
[AI审查] 附件文字提取完成
[AI审查] 调用百炼应用
[AI审查] 百炼应用返回
[AI审查] 审查结果已保存
```

如果页面显示“AI审查：未生成”，优先检查 `backend/.env` 中是否已设置 `DASHSCOPE_API_KEY`、`BAILIAN_REVIEW_APP_ID`、`DB_PASSWORD`，并确认上传附件是可解析的 `docx` 或 `pdf`。
