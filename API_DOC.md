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
*   **参数**:
    *   `role` (Query, 可选): 如果传 `manager`，则会触发基线到工作区的实时增量同步。
*   **同步逻辑 (仅当 `role=manager` 时)**:
    1.  **新增同步**: 基线中存在但工作区不存在的技能，自动同步至工作区。
    2.  **更新同步**: 仅当基线的更新时间 (`mtime`) **晚于** 用户工作空间的最后修改时间时，才自动覆盖同步至工作区；否则保持工作空间的修改状态，不执行覆盖。
    3.  **删除同步**: 工作区存在但基线中已不存在的技能，自动从工作区物理删除。
*   **状态说明**:
    *   `UNCHANGED`: 与基线一致
    *   `MODIFIED`: 用户工作区已修改
    *   `LOCAL_ONLY`: 只在用户工作区存在，基线中不存在
    *   `OUT_OF_SYNC`: 基线有更新
*   **同步机制**:
    *   **Manager 自动同步**: 当 `role=manager` 时，系统会自动执行增量同步（新增、更新、删除）。
    *   **普通用户手动同步**: 非管理员用户若发现 `OUT_OF_SYNC` 状态，可通过 `baseline-sync` 接口（或前端触发相应逻辑）将基线最新内容同步至个人工作区。
*   **示例**:
    ```bash
    curl -X GET "$BASE_URL/v1/skills/user123/agent001/list-with-status?role=manager"
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

### 1.3 技能同步 (双向)
*   **功能**: 
    *   **工作区 -> 基线 (基线化)**: 将指定用户工作区中的技能更新到应用的基线中（仅限管理员使用）。
    *   **基线 -> 工作区 (手动同步)**: 将基线中的最新技能内容覆盖同步到当前用户的工作区。如果基线中不存在该技能但工作区存在（`LOCAL_ONLY`），则会从工作区删除该技能。
*   **URL**: `POST /v1/skills/{userId}/{agentId}/baseline-sync`
*   **参数**:
    *   `name` (Query): 需要同步的技能文件夹名。
    *   `direction` (Query, 可选): 同步方向。
        *   `ws2bl` (默认): 工作区同步至基线 (Workspace to Baseline)。
        *   `bl2ws`: 基线同步至工作区 (Baseline to Workspace)。
*   **示例 (基线同步至工作区)**:
    ```bash
    curl -X POST "$BASE_URL/v1/skills/user123/agent001/baseline-sync?name=weather&direction=bl2ws"
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

### 1.5 删除技能 (操作基线)
*   **功能**: 从全局基线 (`baseline/skills`) 中物理删除指定技能。
*   **URL**: `DELETE /v1/skills/{userId}/{agentId}/delete?name={skillName}`
*   **参数**:
    *   `userId`: 用户唯一标识
    *   `agentId`: 应用唯一标识
    *   `name`: 技能名称 (目录名)
*   **返回示例**:
    ```json
    {
      "status": "success",
      "data": "Successfully deleted skill from workspace: my_skill"
    }
    ```

### 1.6 下载工作区技能
*   **功能**: 将用户工作区下的指定技能打包为 ZIP 文件下载。
*   **URL**: `GET /v1/skills/{userId}/{agentId}/download?name={skillName}`
*   **参数**:
    *   `userId`: 用户唯一标识
    *   `agentId`: 应用唯一标识
    *   `name`: 技能名称 (目录名)
*   **响应**: 二进制流 (application/zip)

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
