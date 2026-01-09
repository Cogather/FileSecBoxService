# FileSecBoxService - Secure Sandbox File Service

This service provides a secure environment for storing and executing "skills" (scripts). It is built with Spring Boot (JDK 8) and designed to run in a Docker container with strict application-level security controls.

## Features

- **Skill Management**: Securely upload ZIP packages, auto-extract, and manage skill metadata (via `SKILL.md`).
- **File Operations**: Full file lifecycle management including recursive listing, full/partial reading, and editing.
- **Sandbox Execution**: Execute scripts in a locked context directory with a strict command whitelist and 5-minute timeouts.
- **Root Security Hardening**: Specialized defenses for Root environments, including path anchoring and deep argument inspection.

## API Endpoints (v1)

All endpoints are prefixed with `/v1/skills`.

### Skill Management
- `POST /v1/skills/{userId}/{agentId}/upload`: Upload and extract skill ZIP.
- `GET /v1/skills/{userId}/{agentId}/list`: List skills with metadata.

### File Operations
- `GET /v1/skills/{userId}/{agentId}/{skillId}/files`: List all files in a skill.
- `GET /v1/skills/{userId}/{agentId}/{skillId}/content`: Read file content (supports line range).
- `PUT /v1/skills/{userId}/{agentId}/{skillId}/edit`: Create or edit files (supports partial replacement).

### Execution
- `POST /v1/skills/{userId}/{agentId}/{skillId}/execute`: Execute whitelisted commands in skill context.

## How to Run

### Using Docker (Recommended)

1. Build the Docker image:
   ```bash
   docker build -t file-sec-box .
   ```

2. Run the container:
   ```bash
   # Mount the persistent volume for WebIDE
   docker run -p 8003:8003 -v /your/host/path:/webIde/product/skill --name filesecbox file-sec-box
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

- **Isolation**: Uses path anchoring to ensure all operations are restricted to `/{userId}/{agentId}/{skillId}/`.
- **Command Whitelist**: Only 17 safe binaries are allowed (e.g., `python3`, `bash`, `ls`).
- **Path Traversal Protection**: Deep scanning of arguments to prevent access to system paths like `/etc/` or `/root/`.
- **Timeouts**: Processes are forcibly killed after 5 minutes to prevent resource exhaustion.
