# FileSecBoxService API 接口文档

> **全局配置**
> - **BASE_URL**: `http://localhost:8003`

---

## 1. 技能管理 (Skills)

### 1.1 上传并解压技能
*   **功能**: 将 ZIP 格式的技能包上传并解压到该应用的技能目录中。
*   **URL**: `POST /v1/skills/{agentId}/upload`
*   **输入**: 
    *   `file` (Multipart): ZIP 压缩文件。
*   **示例**:
    ```bash
    curl -X POST "$BASE_URL/v1/skills/agent001/upload" -F "file=@weather.zip"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": "Upload successful. Details:\n- Skill [weather] uploaded to [/webIde/product/agent001/skills/weather], Status: [Success]"
    }
    ```

### 1.2 查询技能清单
*   **功能**: 返回该应用下所有已安装技能的名称及描述（解析自 `SKILL.md`）。
*   **URL**: `GET /v1/skills/{agentId}/list`
*   **示例**:
    ```bash
    curl -X GET "$BASE_URL/v1/skills/agent001/list"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "name": "weather",
          "description": "查询实时天气预报和预警信息的技能"
        }
      ]
    }
    ```

### 1.3 删除技能
*   **功能**: 删除指定应用的某个技能及其下属的所有文件。
*   **URL**: `DELETE /v1/skills/{agentId}/delete`
*   **参数**: 
    *   `name` (Query): 技能名称（对应文件夹名）。
*   **示例**:
    ```bash
    curl -X DELETE "$BASE_URL/v1/skills/agent001/delete?name=weather"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": "Successfully deleted skill: weather"
    }
    ```

---

## 2. 文件与执行管理 (Sandbox)

### 2.1 上传单一文件
*   **功能**: 上传单个文件到沙箱的通用文件目录 (`files/`) 下。
*   **URL**: `POST /v1/files/{agentId}/upload`
*   **输入**: 
    *   `file` (Multipart): 目标文件。
*   **示例**:
    ```bash
    curl -X POST "$BASE_URL/v1/files/agent001/upload" -F "file=@config.json"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": "File uploaded successfully: files/config.json"
    }
    ```

### 2.2 列出目录清单
*   **功能**: 根据传入的逻辑前缀，递归列出该应用下 `skills/` 或 `files/` 目录中的所有文件。
*   **URL**: `GET /v1/{agentId}/files`
*   **输入**: 
    *   `path` (Query): 必须为 `skills`, `skills/`, `files`, `files/` 或以此为前缀的路径。
*   **示例**:
    ```bash
    curl -X GET "$BASE_URL/v1/agent001/files?path=skills"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": [
        "skills/weather/main.py",
        "skills/weather/SKILL.md"
      ]
    }
    ```

### 2.3 读取文件内容 (支持分页)
*   **功能**: 读取指定文件的内容。支持全量读取或通过行号分页读取。
*   **URL**: `GET /v1/{agentId}/content`
*   **输入**: 
    *   `path` (Query): 文件逻辑路径。
    *   `offset` (Query, 可选): 起始行号，从 1 开始。
    *   `limit` (Query, 可选): 读取的总行数。
*   **示例**:
    ```bash
    curl -X GET "$BASE_URL/v1/agent001/content?path=skills/weather/main.py&offset=1&limit=5"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": {
        "content": "import os\nimport sys...", // 字符串格式 (拼接后的分片或全量)
        "lines": [                         // 列表格式 (按行拆分后的分片或全量)
          "import os",
          "import sys",
          "from utils import log"
        ]
      }
    }
    ```

### 2.4 写入/新建文件
*   **功能**: 在指定逻辑路径下创建新文件或覆盖旧文件。
*   **URL**: `POST /v1/{agentId}/write`
*   **输入 (JSON)**:
    ```json
    {
      "file_path": "files/new_script.py",
      "content": "print('hello from sandbox')"
    }
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": "Successfully created or overwritten file: files/new_script.py"
    }
    ```

### 2.5 精确编辑/替换文件
*   **功能**: 对文件内容进行精确的字符串替换。
*   **URL**: `POST /v1/{agentId}/edit`
*   **输入 (JSON)**:
    ```json
    {
      "file_path": "skills/weather/main.py",
      "old_string": "v1 = 1",
      "new_string": "v1 = 100",
      "expected_replacements": 1
    }
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": "Successfully edited file: skills/weather/main.py"
    }
    ```

### 2.6 在指定上下文执行指令
*   **功能**: 在该应用的根目录下执行受限的系统命令。
*   **URL**: `POST /v1/{agentId}/execute`
*   **输入 (JSON)**:
    ```json
    {
      "command": "python skills/weather/main.py"
    }
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": {
        "stdout": "Weather queried successfully.",
        "stderror": "",
        "exit_code": 0
      }
    }
    ```

### 2.7 删除指定文件
*   **功能**: 删除指定的单个文件或空目录。
*   **URL**: `DELETE /v1/{agentId}/delete`
*   **参数**: 
    *   `path` (Query): 文件的逻辑路径（必须以 `skills/` 或 `files/` 开头）。
*   **示例**:
    ```bash
    curl -X DELETE "$BASE_URL/v1/agent001/delete?path=files/test_echo.txt"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": "Successfully deleted path: files/test_echo.txt"
    }
    ```

---

## 3. 错误响应示例 (Error Responses)

### 3.1 逻辑路径非法
*   **场景**: 传入的路径不以 `skills/` 或 `files/` 开头。
*   **输出**:
    ```json
    {
      "status": "error",
      "data": "Security Error: Path must start with 'skills/' or 'files/'. Current path: temp/test.txt"
    }
    ```

### 3.2 文件不存在
*   **场景**: 读取、编辑或列出不存在的路径。
*   **输出**:
    ```json
    {
      "status": "error",
      "data": "Path not found: skills/wrong_skill/main.py"
    }
    ```

### 3.3 编辑匹配冲突
*   **场景**: `edit` 操作中 `old_string` 实际出现的次数与 `expected_replacements` 不一致。
*   **输出**:
    ```json
    {
      "status": "error",
      "data": "Edit Mismatch: 'old_string' found 3 times, but expected 1 time. Please refine your search string."
    }
    ```

### 3.4 指令被拦截
*   **场景**: 执行不在白名单内的指令（如 `rm -rf`）。
*   **输出**:
    ```json
    {
      "status": "error",
      "data": "Security Error: Command 'rm' is not allowed in whitelist."
    }
    ```

### 3.5 执行超时
*   **场景**: 指令运行时间超过 5 分钟限制。
*   **输出**:
    ```json
    {
      "status": "error",
      "data": "Execution Timeout: Process killed after 300 seconds."
    }
    ```

### 3.6 技能包校验失败
*   **场景**: 上传的 ZIP 包中某个技能目录缺少必须的 `SKILL.md` 文件。
*   **输出**:
    ```json
    {
      "status": "error",
      "data": "Validation Error: Skill [weather] is missing required 'SKILL.md' file."
    }
    ```
