<div align="center">

# CustomServerMessages

**Paper / Folia 服务器进出服消息定制插件**

用可配置的彩色广播，替换原版加入、首次加入、退出与踢出消息。  
可选对接 LuckPerms、CMI Vanish、Vault 经济，让欢迎新人变成可点击、可领奖、防刷的互动体验。

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Paper](https://img.shields.io/badge/Paper-1.21+-00A98F?logo=minecraft&logoColor=white)](https://papermc.io/)
[![Folia](https://img.shields.io/badge/Folia-Supported-2ecc71)](https://papermc.io/software/folia)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue)](https://github.com/D2ach/CustomServerMessages)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Author:** [ohmlnw007](https://github.com)

</div>

---

## ✨ 功能亮点

| 模块 | 说明 |
|------|------|
| **进出服消息** | 自定义 Join / First-Join / Quit / Kick，支持多行、`&` 与 `&#RRGGBB` 颜色 |
| **LuckPerms** | 前缀 / 后缀 / 主组占位符；按权限组匹配专属加入、退出文案 |
| **CMI Vanish** | 隐身进出服不广播；仅对 OP 延迟检测，普通玩家 / 新玩家零延迟 |
| **欢迎领奖** | 新玩家进服后，在线玩家可点击欢迎按钮；**金币发给欢迎者**，可多次点击，同一人对同一新人只发一次 |
| **Vault 经济** | 随机金币区间可配（默认 10–20） |
| **热重载** | `/csm reload` 即时生效，无需重启 |

---

## 📦 环境要求

- **服务端**：Paper / Folia `1.21+`
- **Java**：21+
- **可选依赖**（软依赖，缺哪个用哪个）：
  - [LuckPerms](https://luckperms.net/) — 组消息与 `<lp_*>` 占位符
  - [CMI](https://www.zrips.net/cmi/) — Vanish 静默进出
  - [Vault](https://www.spigotmc.org/resources/vault.34315/) + 任意经济插件 — 欢迎金币

---

## 🚀 安装

1. 下载 `CustomServerMessages-1.0.0.jar`
2. 放入服务器 `plugins/` 目录
3. 启动或重载服务器
4. 编辑 `plugins/CustomServerMessages/config.yml`
5. 执行 `/csm reload`

从源码构建：

```bash
./gradlew jar
# 产物：build/libs/CustomServerMessages-1.0.0.jar
```

---

## ⌨️ 命令与权限

| 命令 | 别名 | 说明 |
|------|------|------|
| `/customservermessages status` | `/csmessages`、`/csm` | 查看插件与集成状态 |
| `/customservermessages reload` | 同上 | 重载配置并重新挂钩依赖 |

| 权限 | 默认 | 说明 |
|------|------|------|
| `customservermessages.admin` | OP | 使用管理命令 |

---

## 🧩 占位符

| 占位符 | 含义 |
|--------|------|
| `<player>` | 玩家名 |
| `<display_name>` | 显示名 |
| `<world>` | 当前世界 |
| `<online>` | 当前在线人数 |
| `<max_online>` | 人数上限 |
| `<reason>` | 踢出原因（仅 kick） |
| `<lp_prefix>` | LuckPerms 前缀 |
| `<lp_suffix>` | LuckPerms 后缀 |
| `<lp_primary_group>` | LuckPerms 主组 |
| `<reward>` | 本次欢迎获得的金币数 |

状态命令额外占位符：`{enabled}`、`{vault}`、`{cmi}`、`{luckperms}`、`{first_join_reward}` 等。

---

## ⚙️ 配置说明

### 开关语义（重要）

```yaml
first-join:
  enabled: true          # 只控制是否广播 first-join.lines
  reward:
    enabled: true        # 只控制是否发送可点击欢迎按钮 / 发奖
```

两者互相独立：关闭首次加入文案，欢迎按钮仍可开启；反之亦然。

### 欢迎按钮防刷规则

1. 新玩家首次进服时，向在线非隐身玩家发送可点击按钮  
2. 按钮可反复点击（有效期可配）  
3. **同一欢迎者对同一新人，仅第一次点击发放金币**  
4. 再次点击只提示「已欢迎过」，不再发钱  
5. **金币发给点击欢迎的玩家，不是新玩家本人**

### 最小示例

```yaml
enabled: true

join:
  enabled: true
  lines:
    - "&a[+] &f<player> 加入了游戏"
  group-messages:
    vip:
      groups: ["vip"]
      lines:
        - "&6[✦] &f<player> 华丽登场"

first-join:
  enabled: true
  lines:
    - "&e欢迎新玩家 &f<lp_prefix><player> &e！"
  reward:
    enabled: true
    min: 10
    max: 20
    button-prefix: "&e新玩家 &f<player> &e加入了！ "
    button: "&a&l[点击欢迎]"
    button-hover: "&7首次点击可获得金币"
    button-lifetime-minutes: 10
    player-message: "&a欢迎 &e<player> &a！你获得了 &e<reward> &a金币。"
    already-claimed: "&7你已经欢迎过 &e<player> &7了。"

quit:
  enabled: true
  lines:
    - "&c[-] &f<player> 离开了服务器"
```

### LuckPerms 组消息

插件按配置顺序匹配：先主组，再继承组。命中即使用该规则的 `lines`，否则回退到默认 `join.lines` / `quit.lines`。

### CMI Vanish

- 隐身进出：不广播进出消息，也不发欢迎按钮  
- 新玩家 / 非 OP：立即处理，无延迟  
- 仅 OP：延迟约 2 tick，等待 CMI 应用隐身状态后再判断  

---

## 🏗️ 项目结构

```text
custom-server-messages/
├── src/main/java/.../CustomServerMessagesPlugin.java
├── src/main/resources/
│   ├── plugin.yml
│   └── config.yml
├── build.gradle.kts
├── LICENSE
└── README.md
```

技术栈：Java 21 · Gradle · Paper API · Adventure（点击回调）· 反射软依赖集成。

---

## 📄 License

本项目采用 [MIT License](LICENSE) 开源。

你可以自由使用、修改、商用再分发，**必须保留版权声明（作者：ohmlnw007）**。

---

## 🤝 贡献

Issue、Pull Request 都欢迎。提交前请确保：

```bash
./gradlew jar
```

能成功构建，并说明改动动机与测试方式。

---

<div align="center">

Made for Paper / Folia · Author **ohmlnw007** · MIT License

</div>
