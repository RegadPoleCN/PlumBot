# 数据库说明

PlumBot 使用 TabooLib 数据库 API 操作 SQLite/MySQL，绑定表结构保持稳定以兼容历史数据。

## 核心类

| 类 | 位置 | 说明 |
|---|---|---|
| `IDatabase` | `common/.../api/database/IDatabase.kt` | 数据库接口 |
| `AbstractBindingDatabase` | `common/.../database/AbstractBindingDatabase.kt` | 绑定表 CRUD 公共实现 |
| `MySQL` | `common/.../database/MySQL.kt` | MySQL 实现，仅负责 Host 与列类型 |
| `SQLite` | `common/.../database/SQLite.kt` | SQLite 实现，仅负责 Host 与列类型 |
| `DatabaseProvider` | `common/.../database/DatabaseProvider.kt` | 数据库实例访问入口 |

## 绑定表结构

表名：`plumbot_binding`（由具体实现决定，通常固定）

| 字段 | 类型 | 说明 |
|---|---|---|
| `userId` | BIGINT / INTEGER | QQ 用户 ID |
| `playerName` | VARCHAR / TEXT | 玩家名 |
| `uuid` | VARCHAR / TEXT | 玩家 UUID（可选） |

> 具体列名与类型以 `AbstractBindingDatabase` 中的 `Table` 定义为准。

## 数据库初始化

`PlumBot.loadDatabase()` 中：

1. 读取 `config.yml` 的 `database.mode`（`sqlite` / `mysql`）。
2. 根据模式创建 `SQLite` 或 `MySQL` 实例。
3. 调用 `DatabaseProvider.start(database)`。
4. 数据库在首次访问时自动创建表。

## 常用 CRUD

`AbstractBindingDatabase` 提供：

- `addBind(userId: Long, playerName: String)`
- `removeBind(playerName: String)`
- `removeBindByNum(userId: Long, num: Int): String?`
- `removeBind(userId: Long)`
- `getBindByUser(userId: String): Map<String, UUID>`
- `getBindByName(name: String): String?`
- `getUUIDByName(name: String): UUID?`
- `getUUIDByUser(userId: String): Map<String, UUID>`

业务代码中通常通过 `DatabaseProvider` 访问：

```kotlin
val wl = DatabaseProvider.getBindByUser(userId.toString())
```

## 关闭连接池

`AbstractBindingDatabase.close()` 会判断 `dataSource` 是否为 `HikariDataSource`，如果是则调用 `close()` 真正关闭连接池；否则回退到归还单条连接。

## 新增表或字段

### 新增字段（绑定表内）

1. 修改 `AbstractBindingDatabase` 中的 `Table` 定义。
2. 在 `IDatabase` 中新增需要公开的方法。
3. 在 `AbstractBindingDatabase` 中实现该方法（MySQL/SQLite 共享逻辑）。
4. 如果 MySQL 与 SQLite 的列类型差异较大，可在子类中覆盖对应常量。
5. 考虑现有用户的表迁移：当前未内置自动迁移，新增非空字段时需要告知用户手动处理或提供默认值。

### 新增独立表

1. 在 `common/.../database/` 下新建数据类与数据库操作类。
2. 建议同样使用 `AbstractBindingDatabase` 模式：公共 CRUD 放在基类，子类只负责 Host 差异。
3. 在 `DatabaseProvider` 中暴露访问入口。

## 注意事项

- **表结构变更属于破坏性变更**，需要谨慎评估。
- 不要在 `common` 之外的模块直接操作数据库表结构。
- 数据库初始化失败时，`PlumBot.loadDatabase()` 会记录错误日志。
