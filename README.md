# FileSecBoxService - Secure Sandbox File Service

This service provides a secure environment for storing and executing "skills" (scripts). It is built with Spring Boot (JDK 8) and designed to run in a Docker container with isolation.

## Features

- **File Storage**: Securely upload and manage skill files.
- **Sandbox Execution**: Execute skills in a restricted environment with timeouts and no-root privileges.
- **Security**: 
    - Path traversal protection.
    - Command injection mitigation.
    - Non-root user execution in Docker.
    - Resource limits (via Docker and process timeouts).

## API Endpoints

### 文件管理 (File Management)
- `POST /api/skills/upload`: 上传 Skill 文件到基线层。
- `GET /api/skills/list`: 获取所有文件列表（基线+覆盖层合并视图）。
- `GET /api/skills/content/{fileName}`: 读取全量文件内容。
- `GET /api/skills/lines/{fileName}?start=1&end=10`: 按行号范围读取内容。
- `PUT /api/skills/edit/{fileName}`: 编辑文件内容（保存至覆盖层）。
- `GET /api/skills/search/name?query=...`: 按文件名搜索。
- `GET /api/skills/search/content?query=...`: 全文搜索内容及行号。

### 脚本执行 (Execution)
- `POST /api/skills/execute/{fileName}`: 执行脚本（优先使用覆盖层版本）。

## How to Run

### Using Docker (Recommended)

1. Build the Docker image:
   ```bash
   docker build -t file-sec-box .
   ```

2. Run the container:
   ```bash
   docker run -p 8003:8003 --name sandbox-service file-sec-box
   ```

### Manual Build

1. Build with Maven:
   ```bash
   mvn clean package
   ```

2. Run the JAR:
   ```bash
   java -jar target/file-sec-box-0.0.1-SNAPSHOT.jar
   ```

## Security Considerations

- **Isolation**: The service runs as a non-root user `sandbox` inside the container.
- **Timeouts**: Skills are killed if they run longer than 30 seconds.
- **Sanitization**: File paths are normalized and checked for traversal attempts.
