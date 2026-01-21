# PlumBot

#### *升级版本时请提前备份配置文件，以防数据丢失*

插件交流群：[825894832](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=-PcufP7TIjLBMOte4H8bHoNmMkP5xZT0&authKey=aPKkGldknKtdCUfX7hhWMFkAOuOpOUYuNZihsUZi9DXvIHVzJhuIRLVfTdCsobZt&noverify=0&group_code=825894832)

![GitHub](https://img.shields.io/github/license/RegadPoleCN/PlumBot)
[![Java CI with Maven](https://github.com/RegadPoleCN/PlumBot/actions/workflows/gradle-v2.yml/badge.svg)](https://github.com/RegadPoleCN/PlumBot/actions/workflows/gradle-v2.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/RegadPoleCN/PlumBot/total?logo=github)](https://github.com/RegadPoleCN/PlumBot/releases)
[![GitHub release (with filter)](https://img.shields.io/github/v/release/RegadPoleCN/PlumBot)](https://github.com/RegadPoleCN/PlumBot/releases)

---

本插件适用于Onebot协议中正向WebSocket连接

**测试环境**：napcat + hytale

## 使用说明

**注意**：
- 序号可以通过查询白名单获得
- 以下命令中 `<ID>` 指代玩家名称，`<QQ>` 指代QQ号
- QQ群内命令前缀可以在配置文件中更改（详见`cmdprefix`字段和`keys`字段）
- 管理员需在配置文件的`admins`字段中进行配置

### 游戏内命令

```bash
/plumbot reload

/plumbot addBind <QQ> <ID>

/plumbot queryBind qq <QQ>
/plumbot queryBind id <ID>

/plumbot deleteBind id <ID>
/plumbot deleteBind qq <QQ>           # 删除此QQ号下所有白名单
/plumbot deleteBind qq <QQ> <序号>    # 删除此QQ号下对应序号的白名单
```

QQ群内命令

玩家命令

```
/申请白名单 <ID>
/查询白名单
/删除白名单 <ID/序号>
/在线人数
```

管理员命令

```
/申请白名单 <QQ> <ID>

/查询白名单 qq:<QQ>
/查询白名单 id:<ID>

/删除白名单 id:<ID>
/删除白名单 qq:<QQ>
/删除白名单 qq:<QQ> <序号>
```

---

## 安全状态

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FRegadPoleCN%2FPlumBot.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FRegadPoleCN%2FPlumBot?ref=badge_large)
[![Security Status](https://www.murphysec.com/platform3/v31/badge/1811686602642419712.svg)](https://www.murphysec.com/console/report/1688753239833206784/1811686602642419712)

## 开源声明

本项目部分代码来自 

[lucko](https://github.com/lucko)：HytaleScheduler部分代码

本项目使用 GNU AGPL 3.0 协议