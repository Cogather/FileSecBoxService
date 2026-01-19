# FileSecBoxService 设计文档

## 1. 整体架构
FileSecBoxService 是一个基于应用（Agent）维度隔离的安全沙箱服务。系统通过逻辑路径前缀（`skills/` 和 `files/`）实现业务功能分流，并在 Root 权限环境下通过严格的路径归一化、指令白名单及环境清理手段保障执行安全。

---

## 2. 存储设计

### 2.1 物理目录布局
所有应用数据均存储在统一的产品根目录下，实现应用间的物理隔离。系统在启动时会根据运行操作系统自动加载 `application.properties` 中对应的路径：

*   **Windows 环境**：使用 `app.product.root.win` 配置（默认 `D:/webIde/product`）。
*   **Linux/Docker 环境**：使用 `app.product.root.linux` 配置（默认 `/webIde/product`）。

| 业务模块 | 逻辑路径前缀 | 物理存储路径 | 存放内容枚举 |
| :--- | :--- | :--- | :--- |
| **技能模块** | `skills/` | `${app.product.root}/{agentId}/skills/` | 技能元数据（SKILL.md）、业务 Python 脚本、技能私有配置。 |
| **通用文件模块** | `files/` | `${app.product.root}/{agentId}/files/` | 统一的沙箱文件处理代码、公共环境配置、工具类脚本。 |

### 2.2 路径校验准则
*   **前缀强制性**：所有 I/O 接口接收的路径参数（`path` 或 `file_path`）必须且只能以 `skills/` 或 `files/` 作为起始字符串。
*   **归一化校验**：系统在操作前必须对路径进行 `normalize` 处理，防止通过 `../` 进行路径穿越。
*   **锚定校验**：物理路径通过 `productRoot.resolve(agentId).resolve(logicalPath)` 计算得到。系统必须校验最终物理路径是否位于 `/webIde/product/{agentId}/` 范围内。
*   **SKILL.md 写入限制**：
    *   在 `skills/` 目录下写入名为 `SKILL.md` 的文件时，路径必须严格遵循 `skills/{skill_name}/SKILL.md` 格式。
    *   禁止在技能子目录下或 `skills/` 根目录下直接创建 `SKILL.md`。

---

## 3. 核心功能设计

### 3.1 文件管理逻辑
*   **全量写入 (Write)**：直接在目标物理路径创建或覆盖文件。系统需拦截非法的 `SKILL.md` 路径。
*   **分页读取 (Content)**：支持通过 `offset`（起始行号，从 1 开始）和 `limit`（读取行数）参数进行行级分页读取，减少内存负载。
*   **精确编辑 (Edit)**：
    *   **匹配机制**：系统读取文件后，寻找 `old_string` 的位置。
    *   **冲突校验**：通过 `expected_replacements` 参数校验匹配到的次数。若实际匹配次数与预期不符，直接返回错误，不执行替换。
    *   **原子性**：替换操作在内存完成后一次性写回文件。
*   **安全删除 (Delete)**：
    *   **技能删除**：递归删除整个技能目录。
    *   **文件删除**：支持删除单个文件。所有删除操作必须通过路径归一化校验，严禁操作非 `skills/` 或 `files/` 目录。

### 3.2 技能生命周期
*   **上传 (Upload)**：接收 ZIP 压缩包，解压至该应用的技能物理目录。支持 `UTF-8` 和 `GBK` 双编码兼容处理。
    *   **合法性校验**：每个技能必须在其根目录下包含 `SKILL.md`。
*   **加载与自愈 (Load & Auto-Healing)**：
    *   **自动向上提取**：在 `list` 接口被调用时，系统会扫描 `skills/` 下的所有一级目录。
    *   **按需自愈**：当用户请求读取 `skills/{skill_name}/SKILL.md` 但该文件物理缺失时，系统会实时触发该技能目录下的递归搜索。若在子目录中发现该文件，则立即完成“上拉”操作并正常返回内容。
    *   **调整逻辑**：如果在一级目录的子文件夹中发现了 `SKILL.md`（如 `skills/A/B/SKILL.md`），系统会自动将其移动到该一级目录的根部（即 `skills/A/SKILL.md`）。
    *   **非侵入性**：仅移动 `SKILL.md` 文件本身。其余代码文件、子目录（如 `B/` 下的脚本）保持原位不动，以避免破坏脚本内部的相对路径引用。
    *   **目的**：确保所有技能的身份标识在物理上始终可见且符合一级目录规范，同时通过按需触发机制保障了查询和读取的 100% 成功率。
    *   **目的**：确保每个一级目录文件夹都能通过其根部的 `SKILL.md` 被识别为合法技能，修复因嵌套创建导致的身份缺失。
*   **查询 (List)**：仅返回已完成自愈、且在合法一级目录下拥有 `SKILL.md` 的技能。
*   **解析**：自动扫描并解析 `SKILL.md`，提取 `name` 和 `description` 字段。

### 3.3 安全执行引擎 (Execute)
*   **工作目录**：必须锚定在逻辑路径对应的物理目录下。
*   **Shell 包装**：系统自动通过 `bash -c` (Linux) 或 `cmd /c` (Windows) 包装指令，原生支持 `>`, `>>`, `|`, `&&` 等 Shell 操作符。
*   **指令白名单**：严格限制仅允许执行以下指令作为首个指令：`python`, `python3`, `bash`, `sh`, `cmd`, `ls`, `cat`, `echo`, `grep`, `sed`, `mkdir`, `touch`, `cp`, `mv`, `rm`, `tee`, `find`, `chmod`, `xargs`, `curl`。
*   **SKILL.md 深度防御策略**：
    *   **事前拦截**：若命令行中出现 `SKILL.md` 字样，系统会强制检查其路径。如果不是 `skills/{name}/SKILL.md` 格式（例如试图在子目录或根目录下操作），则拒绝执行并返回：“Security Error: 'SKILL.md' is a system reserved file. You can only create/edit it at the root of a skill (e.g., skills/my_skill/SKILL.md).”
*   **环境净化**：
    *   **Linux**：清理所有非安全环境变量，强制设置 `PATH=/usr/local/bin:/usr/bin:/bin`。
    *   **Windows**：识别盘符，统一路径分隔符为 `/`，适配系统字符编码。
*   **超时控制**：强制设定 5 分钟（300 秒）执行超时。

---

## 4. 并发与可靠性
*   **锁粒度**：采用 `ReentrantReadWriteLock` 实现按 `agentId` 维度的线程锁。
*   **读写分离**：文件读取接口使用读锁，写入和修改接口使用写锁，确保在高并发下文件不损坏。

---

## 5. 技术栈枚举
*   **JDK**: 1.8
*   **Framework**: Spring Boot 2.3.12.RELEASE
*   **Build Tool**: Maven 3.6
*   **OS Support**: Linux (Production), Windows (Validation)
