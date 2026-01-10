# FileSecBoxService API 接口文档 (v1)

本文档描述了安全沙箱服务提供的 RESTful 接口。所有接口均以 `/v1/skills` 为前缀。

## 统一返回格式
所有接口均返回以下 JSON 结构：
*   `status`: 状态字符串，`success` 或 `error`。
*   `data`: 实际返回的数据内容。

---

## 1. 技能管理 (Skill Management)

### 1.1 上传并解压技能包
上传一个 ZIP 压缩包，系统会自动解析 ZIP 内的技能名，**仅覆盖同名技能目录**，并返回详细的上传报告。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/upload`
*   **请求方式**: `POST`
*   **Content-Type**: `multipart/form-data`
*   **请求参数**:
    *   `file` (File, 必填): ZIP 格式的技能包。
*   **请求示例**:
    ```bash
    curl -X POST -F "file=@my_skill.zip" http://localhost:8003/v1/skills/user123/agent456/upload
    ```
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "Successfully uploaded and extracted 1 skill(s): [my-python-skill]. Target directory: /webIde/product/skill/user123/agent456"
    }
    ```

---

### 1.2 获取技能元数据列表
扫描 Agent 目录下的所有子文件夹，解析其中 `SKILL.md` 的 `name` 和 `description`。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/list`
*   **请求方式**: `GET`
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "name": "文本摘要工具",
          "description": "基于 Python 的自动提取文章摘要技能"
        }
      ]
    }
    ```

---

## 2. 文件深度操作 (File Operations)

### 2.1 获取技能文件清单
递归列出指定技能目录下的所有文件（相对路径）。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/files`
*   **请求方式**: `GET`
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": [
        "SKILL.md",
        "main.py",
        "utils/helper.py"
      ]
    }
    ```

---

### 2.2 查看文件内容 (支持全量/分页)
获取指定文件的文本内容。支持通过 `start` 和 `end` 参数读取特定行。

*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/content`
*   **请求方式**: `GET`
*   **请求参数**:
    *   `path` (String, 必填): 文件相对路径。
    *   `start` (Int, 可选): 起始行号。
    *   `end` (Int, 可选): 结束行号。
*   **返回示例 (分页)**:
    ```json
    {
      "status": "success",
      "data": [
        "import os",
        "def main():"
      ]
    }
    ```

---

### 2.3 编辑或创建文件
在指定的技能目录范围内更新或创建新文件。支持全量覆盖或指定行范围替换。

*   **URL**: `PUT /v1/skills/{userId}/{agentId}/{skillId}/edit`
*   **请求方式**: `PUT`
*   **请求参数**:
    *   `path` (String, 必填): 目标文件相对路径。
    *   `start` (Int, 可选): 替换起始行号。
    *   `end` (Int, 可选): 替换结束行号。
*   **请求体**: `Plain Text` (文件内容)
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "File updated successfully."
    }
    ```

---

## 3. 技能执行 (Execution)

### 3.1 上下文执行受限命令
在指定技能的根目录下执行白名单内的命令。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/{skillId}/execute`
*   **请求方式**: `POST`
*   **请求参数**:
    *   `command` (String, 必填): 白名单命令（如 `bash`, `python3`）。
*   **请求体**: `Array<String>` (命令参数，可选)
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "Exit Code: 0\nOutput:\nHello from Sandbox"
    }
    ```

---

## 4. 安全说明与限制
1.  **路径隔离**: 强制锚定在技能目录。
2.  **Root 权限防护**: 严格白名单与参数深度过滤。
3.  **执行超时**: 硬性限制为 **5 分钟**。
