# NewJoinKit
适用于Paper 1.20.1的轻量级新手礼包插件，支持自定义礼包内容、发放规则和消息。

## 功能特点
- 🎁 自动给首次加入的玩家发放礼包
- 🔧 全配置文件自定义（礼包、消息、延迟发放等）
- 📝 丰富的管理员命令（发放、重置、列表、重载）
- 💾 持久化存储已领取玩家，避免重复发放

## 安装方法
1. 下载最新版本JAR包（Releases页面）；
2. 将JAR包放入服务器`plugins/`目录；
3. 启动服务器生成配置文件；
4. 编辑`plugins/NewJoinKit/config.yml`自定义配置；
5. 重启服务器生效。

## 命令&权限
| 命令 | 描述 | 权限 |
|------|------|------|
| /newjoinkit help | 查看帮助 | 所有人 |
| /newjoinkit give <玩家> | 给玩家发放礼包 | newjoinkit.admin |
| /newjoinkit reset <玩家> | 重置玩家领取记录 | newjoinkit.admin |
| /newjoinkit list | 查看已领取玩家 | newjoinkit.admin |
| /newjoinkit reload | 重载配置 | newjoinkit.admin |
| /newjoinkit test | 给自己测试礼包 | newjoinkit.admin |

## 配置文件示例
```yaml
# settings.yml
settings:
  only-first-join: true  # 仅首次加入发放
  give-delay-ticks: 20   # 延迟发放（20ticks=1秒）
  clear-inventory: false # 是否清空玩家背包
  announce-to-all: true  # 是否全服公告
  announce-message: "&e欢迎新玩家 &a%player% &e加入！已发放新手礼包"
  private-message: "&a你获得了新手礼包！"

items:
  sword:
    material: DIAMOND_SWORD
    amount: 1
    name: "&b新手剑"
    lore:
      - "&7这是给新手的礼物"
      - "&7祝游戏愉快！"
    slot: 0
```

## 下载地址
[[GitHub下载]](https://github.com/MC-anying/NewJoinKit/releases)

## 反馈
有 BUG / 建议请 [在此反馈](https://github.com/MC-anying/NewJoinKit/issues)
