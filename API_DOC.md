# FileSecBoxService API 接口文档 (v1)

本文档描述了安全沙箱服务提供的 RESTful 接口。所有接口均以 `/v1/skills` 为前缀。

## 通用响应格式
所有接口均返回统一的 JSON 结构：
```json
{
  "status": "success", // 或 "error"
  "data": null // 具体的业务数据或错误消息
}
```

## 1. 技能管理 (Skill Management)

### 1.1 上传并解压技能包
上传 ZIP 包，**仅覆盖同名技能目录**。

*   **URL**: `POST /v1/skills/{userId}/{agentId}/upload`
*   **请求方式**: `POST`
*   **Content-Type**: `multipart/form-data`
*   **请求参数**: `file` (ZIP 文件)
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "my-python-skill, helper-tool"
    }
    ```

---

### 1.2 获取技能元数据列表
*   **URL**: `GET /v1/skills/{userId}/{agentId}/list`
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
*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/files`
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": ["main.py", "config.json"]
    }
    ```

---

### 2.2 查看文件内容
*   **URL**: `GET /v1/skills/{userId}/{agentId}/{skillId}/content`
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "import os\n..."
    }
    ```

---

### 2.3 编辑或创建文件
*   **URL**: `PUT /v1/skills/{userId}/{agentId}/{skillId}/edit`
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "File updated."
    }
    ```

---

## 3. 技能执行 (Execution)

### 3.1 上下文执行受限命令
*   **URL**: `POST /v1/skills/{userId}/{agentId}/{skillId}/execute`
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "Exit Code: 0\nOutput:\nHello World"
    }
    ```

---

## 4. 安全说明与限制
1.  **路径隔离**: 严禁 `../` 越界。
2.  **Root 权限防护**: 基于 17 个命令白名单及参数路径过滤。
3.  **执行超时**: 硬性限制为 **5 分钟**。
