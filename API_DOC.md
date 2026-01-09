# FileSecBoxService API 接口文档 (v1)

本文档描述了安全沙箱服务提供的 RESTful 接口。所有接口均以 `/v1/skills` 为前缀。

## 1. 技能管理 (Skill Management)

### 1.1 上传并解压技能包
上传一个 ZIP 压缩包，系统会自动创建用户与 Agent 的关联目录，并安全地解压覆盖旧数据。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/upload`
*   **请求方式**: `POST`
*   **Content-Type**: `multipart/form-data`
*   **请求参数**:
    *   `file` (File, 必填): ZIP 格式的技能包。
*   **请求示例**:
    ```bash
    curl -X POST -F "file=@my_skill.zip" http://localhost:8080/v1/skills/user123/agent456/upload
    ```
*   **返回体**: `String`
*   **返回示例**:
    ```text
    Skill uploaded and extracted.
    ```

---

### 1.2 获取技能元数据列表
扫描 Agent 目录下的所有子文件夹，解析其中 `SKILL.md` 的 `name` 和 `description`。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/list`
*   **请求方式**: `GET`
*   **请求示例**:
    ```bash
    curl http://localhost:8080/v1/skills/user123/agent456/list
    ```
*   **返回体**: `Array<Object>`
*   **返回示例**:
    ```json
    [
      {
        "name": "文本摘要工具",
        "description": "基于 Python 的自动提取文章摘要技能",
        "skillId": "text-summary-v1",
        "path": "/webIde/product/skill/user123/agent456/text-summary-v1"
      }
    ]
    ```

---

## 2. 文件深度操作 (File Operations)

### 2.1 获取技能文件清单
递归列出指定技能目录下的所有文件（相对路径）。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/files`
*   **请求方式**: `GET`
*   **请求示例**:
    ```bash
    curl http://localhost:8080/v1/skills/user123/agent456/skill001/files
    ```
*   **返回体**: `Array<String>`
*   **返回示例**:
    ```json
    [
      "SKILL.md",
      "main.py",
      "utils/helper.py",
      "config.json"
    ]
    ```

---

### 2.2 查看文件内容 (支持全量/分页)
获取指定文件的文本内容。支持通过 `start` 和 `end` 参数读取特定行。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/content`
*   **请求方式**: `GET`
*   **请求参数**:
    *   `path` (String, 必填): 文件相对路径（如 `utils/helper.py`）。
    *   `start` (Int, 可选): 起始行号（从 1 开始）。
    *   `end` (Int, 可选): 结束行号。
*   **请求示例 (按行读取)**:
    ```bash
    curl "http://localhost:8080/v1/skills/user123/agent456/skill001/content?path=main.py&start=1&end=10"
    ```
*   **返回体**: `String` (全量) 或 `Array<String>` (分页)
*   **返回示例 (分页)**:
    ```json
    [
      "import os",
      "import sys",
      "def main():",
      "    print('Hello World')"
    ]
    ```

---

### 2.3 编辑或创建文件
在指定的技能目录范围内更新或创建新文件。支持全量覆盖或指定行范围替换。

*   **URL**: `PUT /v1/skills/{userId}/{agentId}/{skillId}/edit`
*   **请求方式**: `PUT`
*   **请求参数**:
    *   `path` (String, 必填): 目标文件相对路径。
    *   `start` (Int, 可选): 替换起始行号（从 1 开始）。
    *   `end` (Int, 可选): 替换结束行号。
*   **请求体**: `Plain Text` (文件内容)
*   **请求示例 (局部替换第 5-10 行)**:
    ```bash
    curl -X PUT -H "Content-Type: text/plain" \
         --data "NEW_LINE_CONTENT" \
         "http://localhost:8080/v1/skills/user123/agent456/skill001/edit?path=config.json&start=5&end=10"
    ```
*   **返回体**: `String`
*   **返回示例**: `File updated.`

---

## 3. 技能执行 (Execution)

### 3.1 上下文执行受限命令
在指定技能的根目录下执行白名单内的命令。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/{skillId}/execute`
*   **请求方式**: `POST`
*   **请求参数**:
    *   `command` (String, 必填): 白名单命令（如 `bash`, `python3`, `ls`）。
*   **请求体**: `Array<String>` (命令参数，可选)
*   **请求示例 (通过 Bash 创建并写入文件)**:
    ```bash
    curl -X POST -H "Content-Type: application/json" \
         -d '["-c", "echo \"print(\"hello\")\" > dynamic_script.py"]' \
         "http://localhost:8080/v1/skills/user123/agent456/skill001/execute?command=bash"
    ```
*   **返回体**: `String` (标准输出与标准错误的合并结果)
*   **返回示例**:
    ```text
    Exit Code: 0
    Output:
    Processing input: test
    Task completed successfully.
    ```

---

## 4. 安全说明与限制

1.  **路径隔离**: 所有的 `path` 或 `skillId` 均经过强制校验，严禁 `../` 越界。
2.  **Root 权限防护**: 命令执行受白名单限制，且参数中严禁出现 `/etc/`, `/root/` 等敏感系统路径。
3.  **执行超时**: 硬性限制为 **5 分钟**。
