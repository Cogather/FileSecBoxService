# FileSecBoxService API 接口文档 (v1)

本文档描述了安全沙箱服务提供的 RESTful 接口。服务包含**技能管理**（前缀 `/v1/skills`）与**通用沙箱**（前缀 `/v1/sandbox`）两套 API 体系。

## 统一返回格式
所有接口均返回以下 JSON 结构：
*   `status`: 状态字符串，`success` 或 `error`。
*   `data`: 实际返回的数据内容。

---

## 1. 技能管理 - SkillController
所有技能相关接口以 `/v1/skills` 为前缀。

### 1.1 上传并解压技能包
上传一个 ZIP 压缩包。支持通过 `scope` 参数决定是配置应用公共技能还是用户覆盖层。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/upload`
*   **请求方式**: `POST`
*   **参数**: 
    *   `file` (MultipartFile, 必填)
    *   `scope` (String, 可选): `baseline` 或 `overlay` (默认)
*   **示例请求**:
    ```bash
    curl -X POST "http://localhost:8003/v1/skills/1001/agent001/upload?scope=overlay" \
         -F "file=@myskill.zip"
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": "Upload successful: skill 'myskill' to '/webIde/product/skill/overlay/1001/agent001/myskill'"
    }
    ```

### 1.2 获取技能元数据列表
扫描 Agent 的应用公共技能目录和用户覆盖层目录，返回合并后的技能列表。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/list`
*   **请求方式**: `GET`
*   **示例请求**:
    ```bash
    curl "http://localhost:8003/v1/skills/1001/agent001/list"
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "name": "天气查询技能",
          "description": "提供全球实时天气查询服务"
        },
        {
          "name": "我的自定义工具",
          "description": "No description available."
        }
      ]
    }
    ```

### 1.3 查看技能文件清单
递归列出指定技能目录下的所有文件。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/files`
*   **请求方式**: `GET`
*   **示例请求**:
    ```bash
    curl "http://localhost:8003/v1/skills/1001/agent001/weather_skill/files"
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": [
        "main.py",
        "utils/helper.py",
        "SKILL.md"
      ]
    }
    ```

### 1.4 查看文件内容
获取指定文件的文本内容。若不传分页参数，返回 **String**（全量内容）；若传 `start` 和 `end`，返回 **Array<String>**（分页行列表）。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/content`
*   **请求方式**: `GET`
*   **参数**: 
    *   `path` (String, 必填): 相对路径，如 `main.py`
    *   `start` (Integer, 可选): 起始行号（从 1 开始）
    *   `end` (Integer, 可选): 结束行号
*   **示例请求 1 (全量读取)**:
    ```bash
    curl "http://localhost:8003/v1/skills/1001/agent001/weather_skill/content?path=main.py"
    ```
*   **示例响应 1 (String)**:
    ```json
    {
      "status": "success",
      "data": {
        "content": "import sys\nimport os\n\ndef main():\n    print('Hello Sandbox')"
      }
    }
    ```
*   **示例请求 2 (分页读取)**:
    ```bash
    curl "http://localhost:8003/v1/skills/1001/agent001/weather_skill/content?path=main.py&start=1&end=2"
    ```
*   **示例响应 2 (Array<String>)**:
    ```json
    {
      "status": "success",
      "data": {
        "content": [
          "import sys",
          "import os"
        ]
      }
    }
    ```

### 1.5 上下文执行受限命令
在指定技能的根目录下执行白名单内的命令。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/{skillId}/execute`
*   **请求方式**: `POST`
*   **参数**: 
    *   `command` (String, 必填): 白名单内的指令，如 `python3`
*   **请求体**: `Array<String>` (参数列表)
*   **示例请求**:
    ```bash
    curl -X POST "http://localhost:8003/v1/skills/1001/agent001/weather_skill/execute?command=python3" \
         -H "Content-Type: application/json" \
         -d '["main.py", "--city", "Beijing"]'
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": {
        "stdout": "Beijing: 25°C, Sunny",
        "stderror": "",
        "exit_code": 0
      }
    }
    ```

---

## 2. 通用沙箱操作 - SandboxController
通用的隔离区操作接口，前缀为 `/v1/sandbox`。不再局限于技能目录，但在用户/应用级别进行物理隔离。

### 2.1 编辑或创建文件
在指定的沙箱目录范围内更新或创建新文件。支持全量覆盖或指定行范围替换。

*   **URL**: `PUT /v1/sandbox/{userId}/{agentId}/edit`
*   **请求方式**: `PUT`
*   **参数**: 
    *   `path` (String, 必填): 相对沙箱根目录的路径
    *   `start`, `end` (Integer, 可选): 用于行替换
*   **请求体**: `String` (纯文本内容)
*   **示例请求**:
    ```bash
    curl -X PUT "http://localhost:8003/v1/sandbox/1001/agent001/edit?path=temp.txt" \
         -H "Content-Type: text/plain" \
         -d "Hello General Sandbox"
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": "Sandbox file updated successfully."
    }
    ```

### 2.2 安全执行受限命令
在用户的沙箱工作根目录下执行指令。

*   **URL**: `POST /v1/sandbox/{userId}/{agentId}/execute`
*   **请求方式**: `POST`
*   **参数**: 
    *   `command` (String, 必填): 指令名，如 `python3`, `ls` 等
*   **请求体**: `Array<String>` (参数列表)
*   **示例请求**:
    ```bash
    curl -X POST "http://localhost:8003/v1/sandbox/1001/agent001/execute?command=ls" \
         -H "Content-Type: application/json" \
         -d '["-la"]'
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": {
        "stdout": "total 8\ndrwxr-xr-x 2 root root 4096 Jan 10 10:00 .\n...",
        "stderror": "",
        "exit_code": 0
      }
    }
    ```

### 2.3 查看沙箱文件清单
列出通用沙箱目录下的所有文件。

*   **URL**: `GET /v1/sandbox/{userId}/{agentId}/files`
*   **请求方式**: `GET`
*   **示例请求**:
    ```bash
    curl "http://localhost:8003/v1/sandbox/1001/agent001/files"
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": ["temp.txt", "data/log.txt"]
    }
    ```

### 2.4 查看沙箱文件内容
获取通用沙箱内文件的内容。若不传分页参数，返回 **String**（全量内容）；若传 `start` 和 `end`，返回 **Array<String>**（分页行列表）。

*   **URL**: `GET /v1/sandbox/{userId}/{agentId}/content`
*   **请求方式**: `GET`
*   **参数**: 
    *   `path` (String, 必填): 相对路径
    *   `start` (Integer, 可选): 起始行号
    *   `end` (Integer, 可选): 结束行号
*   **示例请求 1 (全量读取)**:
    ```bash
    curl "http://localhost:8003/v1/sandbox/1001/agent001/content?path=temp.txt"
    ```
*   **示例响应**:
    ```json
    {
      "status": "success",
      "data": {
        "content": "Hello General Sandbox content\nLine 2\nLine 3"
      }
    }
    ```
*   **示例请求 2 (分页读取)**:
    ```bash
    curl "http://localhost:8003/v1/sandbox/1001/agent001/content?path=temp.txt&start=1&end=2"
    ```
*   **示例响应 2 (Array<String>)**:
    ```json
    {
      "status": "success",
      "data": {
        "content": [
          "Hello General Sandbox content",
          "Line 2"
        ]
      }
    }
    ```

---

## 3. 安全说明与限制
1.  **路径隔离**: 技能操作锚定在技能目录，通用沙箱操作锚定在 `/sandbox/{userId}/{agentId}/`。
2.  **Root 权限防护**: 严格指令白名单（python3, pip, bash, ls, cat, grep, sed, awk, echo, cp, mv, rm, mkdir, find, curl, sh, pip3）。
3.  **分层存储**: 仅 Skill 模块支持应用公共技能 (baseline) 与用户覆盖层 (overlay) 的分层。
4.  **执行超时**: 硬性限制为 **5 分钟**。
