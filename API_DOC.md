# FileSecBoxService API 接口文档

> **全局配置更新**
> - **BASE_URL**: `http://localhost:8003`
> - **路径模式**: 接口中增加了 `{userId}` 参数，用于实现用户级的工作区隔离。

---

## 1. 技能管理 (Skills)

### 1.1 查询技能列表 (基础版)
*   **功能**: 返回当前用户工作区下的技能列表，仅包含基本信息（名称、描述）。
*   **URL**: `GET /v1/skills/{userId}/{agentId}/list`
*   **示例**:
    ```bash
    curl -X GET "$BASE_URL/v1/skills/user123/agent001/list"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "name": "weather",
          "description": "天气查询"
        }
      ]
    }
    ```

### 1.2 查询技能列表 (带状态比对)
*   **功能**: 返回当前用户工作区下的技能列表，并计算相对于基线的状态。
*   **URL**: `GET /v1/skills/{userId}/{agentId}/list-with-status`
*   **状态说明**:
    *   `UNCHANGED`: 与基线一致
    *   `MODIFIED`: 用户已修改
    *   `NEW`: 用户新增
    *   `OUT_OF_SYNC`: 基线有更新
    *   `DELETED`: 基线存在，但用户在工作区已将其删除。
*   **操作逻辑**: 对于 `DELETED` 状态的技能，用户依然可以通过 `baseline-sync` 接口将其从基线中彻底删除。
*   **示例**:
    ```bash
    curl -X GET "$BASE_URL/v1/skills/user123/agent001/list-with-status"
    ```
*   **输出**:
    ```json
    {
      "status": "success",
      "data": [
        {
          "name": "weather",
          "description": "天气查询",
          "status": "MODIFIED",
          "lastSyncTime": "2026-01-20 10:00:00"
        }
      ]
    }
    ```

### 1.3 技能基线化 (单技能)
*   **功能**: 将指定用户工作区中的技能更新到应用的基线中（仅限应用管理员使用）。
*   **URL**: `POST /v1/skills/{userId}/{agentId}/baseline-sync`
*   **参数**:
    *   `name` (Query): 需要基线化的技能文件夹名。
*   **示例**:
    ```bash
    curl -X POST "$BASE_URL/v1/skills/admin_user/agent001/baseline-sync?name=weather"
    ```

### 1.4 上传技能 (上传至基线)
*   **功能**: 上传一个包含多个技能目录的 Zip 包，系统会自动解析、验证并更新至全局基线 (`baseline/skills`)。
*   **URL**: `POST /v1/skills/{userId}/{agentId}/upload`
*   **参数**:
    *   `file` (Multipart): 包含技能目录的 Zip 文件。
*   **规则**:
    *   每个技能目录内必须包含 `SKILL.md`。
    *   上传后会覆盖基线中同名的技能。
    *   此操作通常由管理员执行，用于分发应用级的基础技能。
*   **示例**:
    ```bash
    curl -X POST "http://localhost:8003/v1/skills/admin/agent001/upload" -F "file=@myskills.zip"
    ```

---

## 2. 文件与执行管理 (Sandbox)

### 2.1 列出目录清单
*   **功能**: 递归列出用户工作区下的文件。
*   **URL**: `GET /v1/{userId}/{agentId}/files`
*   **参数**: `path` (Query) - 例如 `skills/` 或 `files/`

### 2.2 读取文件内容
*   **URL**: `GET /v1/{userId}/{agentId}/content`
*   **参数**: `path`, `offset` (可选), `limit` (可选)

### 2.3 写入文件
*   **URL**: `POST /v1/{userId}/{agentId}/write`
*   **Body (JSON)**: `{"file_path": "...", "content": "..."}`

### 2.4 精确编辑
*   **URL**: `POST /v1/{userId}/{agentId}/edit`
*   **Body (JSON)**: `{"file_path": "...", "old_string": "...", "new_string": "...", "expected_replacements": 1}`

### 2.5 执行指令
*   **功能**: 在租户隔离的工作区 `${app.product.root}/{agentId}/workspaces/{userId}/` 目录下执行指令。
*   **URL**: `POST /v1/{userId}/{agentId}/execute`
*   **Body (JSON)**: `{"command": "..."}`
*   **说明**: 物理路径对用户透明，用户仅需关注逻辑路径。

### 2.6 删除文件
*   **URL**: `DELETE /v1/{userId}/{agentId}/delete`

---

## 3. Skill-Creator 特殊访问逻辑
*   **重定向**: 任何针对 `skills/skill-creator/...` 路径的读写请求，服务端将自动忽略 `{userId}` 的隔离，强制路由至全局 `${app.product.root}/skill-creator/` 目录。
*   **写保护**: 除管理员外，普通用户对 `skill-creator` 目录仅拥有只读权限（或限制其修改核心工具逻辑）。

---

## 4. 自动清理
*   **周期**: 每 1 小时扫描一次。
*   **阈值**: 闲置超过 12 小时的用户工作区将被自动销毁。下一次用户访问时，系统将重新从 `baseline/` 同步。
