# FileSecBoxService 设计文档

## 1. 项目概述
FileSecBoxService 是一个基于 Java 8 开发的安全沙箱服务，专门用于管理和执行通过 WebIDE 上传的“技能（Skills）”。由于该服务在容器内以 **Root 权限** 运行，系统通过严苛的应用层“白名单控制”与“路径锚定”技术，构建了一个逻辑上的受限沙箱环境。

## 2. 存储与组织结构

### 2.1 目录规范
*   **物理根路径**: `/webIde/product/skill` (WebIDE 持久化挂载点)。
*   **逻辑层级**: `/{userId}/{agentId}/{skillId}/`。

### 2.2 存储安全策略
*   **技能级覆盖 (Skill-level Overwrite)**: 
    *   上传 ZIP 时，系统会解析 ZIP 内包含的技能目录名。
    *   **仅删除** ZIP 中出现的同名技能目录，保留该 `agentId` 下的其他技能。
    *   实现增量更新与局部覆盖，防止误删同一 Agent 下的其他技能。
*   **Zip Slip 防御**: 解压过程中，每个条目路径必须通过 `normalize()` 规范化，且必须通过 `startsWith(agentDir)` 校验。任何包含 `../` 的尝试都将直接导致解压失败并抛出安全异常。

## 3. 安全隔离与执行限制 (Root 权限加固方案)

### 3.1 核心命令白名单 (Strict Binary Whitelist)
系统仅允许执行以下 **17 个** 二进制程序。除此之外的任何命令调用（包括但不限于 `rm -rf /`, `chmod 777 /etc` 等）都将被拦截：
*   **脚本引擎**: `python3`, `bash`, `sh`
*   **文件操作**: `ls`, `cat`, `mkdir`, `touch`, `cp`, `mv`, `rm`, `tee`
*   **文本/流处理**: `echo`, `grep`, `sed`, `find`, `chmod`, `xargs`

### 3.2 参数层级过滤 (Argument Content Inspection)
即使在白名单内的命令，其参数也将受到实时扫描。若参数中出现以下任何特征，执行请求将立即被阻断：
*   **逃逸符**: `..` (严禁任何形式的向上回溯)
*   **系统敏感路径**: `/etc/`, `/root/`, `/proc/`, `/dev/`, `/sys/`, `/boot/`
*   **危险 Flag**: 禁止命令参数中包含 `--privileged`, `eval`, `exec` 等可能导致提权的关键字。

### 3.3 环境净化策略 (Environment Sanitization)
为了防止 Root 容器的环境变量（如集群 Secret）泄露，系统采用“安全白名单继承”模式：
*   **允许保留的变量**: `PATH`, `LANG`, `LC_ALL`, `HOME`, `USER`, `PWD`
*   **强制剔除的变量**: 所有 K8S 注入变量（`KUBERNETES_...`）、数据库密钥、凭证信息等。
*   **路径强制重置**: `PATH` 变量被重写为标准的 `/usr/local/bin:/usr/bin:/bin`。

### 3.4 上下文锁定与跨用户隔离 (Multi-tenant Isolation)
*   **工作目录锁定**: 每次执行命令前，系统强制调用 `ProcessBuilder.directory(skillRoot)`。这意味着所有相对路径操作（如 `mkdir tmp`）都会自动作用在技能目录下。
*   **绝对路径强制锚定**: 
    *   系统会对所有命令参数进行扫描。
    *   若参数是以 `/` 开头的绝对路径，系统会校验其是否以 `/{userId}/{agentId}/{skillId}/` 为前缀。
    *   **结果**: 即使是 Root 权限，`user_a` 的脚本也无法通过 `ls /webIde/product/skill/user_b` 来窥探或修改 `user_b` 的数据。
*   **超时硬限制**: 任何脚本的执行时间不得超过 **5 分钟** (300 秒)，超时将触发强制 `destroyForcibly()`。

## 4. 接口设计 (API v1)

### 4.1 核心接口列表
1.  `POST /v1/skills/{userId}/{agentId}/upload`: 安全上传并覆盖。
2.  `GET /v1/skills/{userId}/{agentId}/list`: 递归解析 `SKILL.md` 返回元数据。
3.  `GET /v1/skills/{userId}/{agentId}/{skillId}/files`: 递归列出文件列表。
4.  `GET /v1/skills/{userId}/{agentId}/{skillId}/content`: 查看文件内容 (支持行范围读取)。
5.  `PUT /v1/skills/{userId}/{agentId}/{skillId}/edit`: 覆盖或局部行替换写入。
6.  `POST /v1/skills/{userId}/{agentId}/{skillId}/execute`: 在指定技能的工作目录下执行命令。
