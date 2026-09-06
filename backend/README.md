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
cd "C:\Users\asus\Desktop\files\Coding\Web\contract_management_system\backend"
Copy-Item .env.example .env
```

`.env` 已被 Git 忽略，不会提交到仓库；仓库只提交 `.env.example` 这种占位模板。从仓库根目录启动时，后端也会自动尝试读取 `backend/.env`。

## 数据库配置

默认启用 `db` profile，需要在 `backend/.env` 中提供：

```text
DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password
```

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
BAILIAN_APP_ID=your_legacy_default_app_id
AI_REVIEW_ENABLED=true
```

`BAILIAN_REVIEW_APP_ID` 用于 AI 审查智能体；“四海”聊天智能体固定使用应用 ID `84a6acc6f6134090b9b21e488c3792f1`。旧配置 `BAILIAN_APP_ID` 仍保留为兼容默认值。

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

完整临时启动示例：

```powershell
cd "C:\Users\asus\Desktop\files\Coding\Web\contract_management_system\backend"
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
