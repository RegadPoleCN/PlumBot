# PlumBot

#### *Please back up the configuration file before upgrading to prevent data loss*

Plugin communication group: [825894832](http://qm.qq.com/cgi-bin/qm/qr?_wv=1027&k=-PcufP7TIjLBMOte4H8bHoNmMkP5xZT0&authKey=aPKkGldknKtdCUfX7hhWMFkAOuOpOUYuNZihsUZi9DXvIHVzJhuIRLVfTdCsobZt&noverify=0&group_code=825894832)

![GitHub](https://img.shields.io/github/license/RegadPoleCN/PlumBot)
[![Java CI with Maven](https://github.com/RegadPoleCN/PlumBot/actions/workflows/gradle-v2.yml/badge.svg)](https://github.com/RegadPoleCN/PlumBot/actions/workflows/gradle-v2.yml)
[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/RegadPoleCN/PlumBot/total?logo=github)](https://github.com/RegadPoleCN/PlumBot/releases)
[![GitHub release (with filter)](https://img.shields.io/github/v/release/RegadPoleCN/PlumBot)](https://github.com/RegadPoleCN/PlumBot/releases)

---

This plugin is suitable for Onebot protocol's forward WebSocket connection.

**Test environment**: napcat + hytale

## Usage Instructions

**Notes**:
- Serial numbers can be obtained by querying the whitelist
- In the following commands, `<ID>` refers to the player name, `<QQ>` refers to the QQ number
- QQ group command prefixes can be changed in the configuration file (see `cmdprefix` and `keys` fields)
- Administrators need to be configured in the `admins` field of the configuration file

### In-game Commands

```bash
/plumbot reload

/plumbot addBind <QQ> <ID>

/plumbot queryBind qq <QQ>
/plumbot queryBind id <ID>

/plumbot deleteBind id <ID>
/plumbot deleteBind qq <QQ>           # Delete all whitelists under this QQ number
/plumbot deleteBind qq <QQ> <serial>  # Delete the whitelist corresponding to the serial number under this QQ number
```

QQ Group Commands

Player Commands

```
/申请白名单 <ID>          # Apply for whitelist <ID>
/查询白名单                # Query whitelist
/删除白名单 <ID/序号>      # Delete whitelist <ID/serial>
/在线人数                  # Online player count
```

Administrator Commands

```
/申请白名单 <QQ> <ID>     # Apply for whitelist <ID> for QQ number <QQ>

/查询白名单 qq:<QQ>       # Query whitelist by QQ number
/查询白名单 id:<ID>       # Query whitelist by player name

/删除白名单 id:<ID>       # Delete whitelist by player name
/删除白名单 qq:<QQ>       # Delete all whitelists for QQ number
/删除白名单 qq:<QQ> <序号> # Delete whitelist by QQ number and serial
```

---

## Security Status

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FRegadPoleCN%2FPlumBot.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FRegadPoleCN%2FPlumBot?ref=badge_large)
[![Security Status](https://www.murphysec.com/platform3/v31/badge/1811686602642419712.svg)](https://www.murphysec.com/console/report/1688753239833206784/1811686602642419712)

## Open Source Declaration

Part of this project's code comes from:

[lucko](https://github.com/lucko): Part of HytaleScheduler code

This project uses GNU AGPL 3.0 license