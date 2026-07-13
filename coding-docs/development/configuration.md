# 配置系统说明

PlumBot 使用 [Configurate](https://github.com/SpongePowered/Configurate) 读取 YAML/HOCON 配置，并通过 `AbstractConfigurator` 统一封装路径解析与类型读取。

## 核心类

| 类 | 位置 | 说明 |
|---|---|---|
| `AbstractConfigurator` | `common/.../api/config/AbstractConfigurator.kt` | YAML/HOCON 公共基类，统一 vararg 路径解析 |
| `YamlConfigurator` | `common/.../api/config/YamlConfigurator.kt` | YAML 配置读取 |
| `HoconConfigurator` | `common/.../api/config/HoconConfigurator.kt` | HOCON 配置读取（保留备用） |
| `Messages` | `common/.../api/config/Messages.kt` | 消息模板对象，反射加载 |

## 配置文件

| 文件 | 资源路径 | 运行时路径 | 用途 |
|---|---|---|---|
| `config.yml` | `common/src/main/resources/config.yml` | `<dataDirectory>/config.yml` | 主配置：bot 类型、群号、功能开关、命令前缀 |
| `datasource.yml` | `common/src/main/resources/datasource.yml` | `<dataDirectory>/datasource.yml` | 数据库配置 |
| `messages.yml` | `common/src/main/resources/messages.yml` | `<dataDirectory>/messages.yml` | 消息模板与占位符 |

首次启动时，若文件不存在，会从资源目录复制到数据目录。

## 读取配置

使用 `vararg nodePath` 按层级读取，支持 `a.b.c` 路径拆分：

```kotlin
val enabled = config.getBoolean("feature", "join", "enable")
val groups = config.getLongList("groups")
val prefix = config.getString("feature", "cmdPrefix") ?: ""
val maxNum = config.getInteger("feature", "bind", "maxNum")
```

可用方法：

- `getString(vararg nodePath: String?): String?`
- `getLong(vararg nodePath: String?): Long`
- `getInteger(vararg nodePath: String?): Int`
- `getBoolean(vararg nodePath: String?): Boolean`
- `getStringList(vararg nodePath: String?): List<String?>`
- `getLongList(vararg nodePath: String?): List<Long>`
- `getNode(vararg nodePath: String?): ConfigurationNode`
- `getConfigMaker(vararg nodePath: String?): C`

### 路径解析规则

`getString("feature", "join", "enable")` 等价于 YAML 路径 `feature.join.enable`。`AbstractConfigurator.resolveNode` 会自动按 `.` 拆分，因此也支持 `getString("feature.join.enable")`。

## `config.yml` 结构概览

```yaml
bot:
  type: "onebot"  # onebot / mirai
  onebot:
    # OneBot 相关配置
  miraimc:
    # MiraiMC 相关配置

groups:
  - 123456789

feature:
  cmdPrefix: "/"
  bind:
    enable: true
    maxNum: 3
  join:
    enable: true
    pic: false
  leave:
    enable: true
    pic: false
  chat:
    enable: true
  load:
    enable: true
    pic: false
  img:
    file: "%plugin_folder%/font.ttf"

keys:
  list:
    - "list"
  addBind:
    - "bind"
  deleteBind:
    - "unbind"
  queryBind:
    - "query"
```

## 新增配置项

1. 在 `common/src/main/resources/config.yml` 中新增默认值。
2. 在代码中使用 `config.getXxx(...)` 读取。
3. 如果配置项控制功能开关，建议在 `feature` 节下组织。
4. 如果配置项是命令关键字，在 `keys` 节下组织。

## 注意事项

- 配置键一旦确定，修改需谨慎，避免破坏用户现有配置。
- `messages.yml` 的加载逻辑见 [消息模板](./messages.md)。
- `datasource.yml` 的加载与数据库初始化见 [数据库](./database.md)。
