# ⚔ MythicWeapon

> Plugin Minecraft độc lập cung cấp hệ thống vũ khí huyền thoại với skill passive/active hoàn toàn có thể cấu hình.

---

## 📋 Mục lục
- [Yêu cầu](#-yêu-cầu)
- [Cài đặt](#-cài-đặt)
- [Vũ khí hiện tại](#-vũ-khí-hiện-tại)
- [Lệnh](#-lệnh)
- [Cấu hình](#-cấu-hình)
- [Hệ thống Auto-Update](#-hệ-thống-auto-update)
- [Kiến trúc](#-kiến-trúc)

---

## 🔧 Yêu cầu

| Yêu cầu | Phiên bản |
|---------|-----------|
| Java | 21+ |
| Minecraft Server | 1.21.x (Paper / Folia) |
| Soft Dependency | [Nexo](https://github.com/Nexo) *(tuỳ chọn — custom texture)* |

---

## 📦 Cài đặt

1. Đặt `MythicWeapon.jar` vào thư mục `plugins/`
2. Khởi động server → plugin tự sinh `weapons.yml` và `messages.yml`
3. Cấu hình theo ý muốn
4. `/mw reload` để áp dụng thay đổi

---

## ⚔ Vũ khí hiện tại

### 🗡 Cursed Rod *(ROD)*
| Loại | Mô tả |
|------|-------|
| **Passive** | Khi câu trúng Player → **đánh cắp Totem** của họ, hồi máu cho người dùng |
| **Active** | Khi đang cooldown → **chặn không cho quăng cần** (PlayerFishEvent blocked) |

---

### 🔱 Void Spear *(SPEAR)*
| Loại | Mô tả |
|------|-------|
| **Passive** | Mỗi đòn có tỉ lệ làm địch **phát sáng (Glowing)** + `+10% damage` |
| **Active** | Nhận **Speed III + Jump II** trong 5s. Đánh trúng địch đang phát sáng: `+50% damage`. Đánh địch thường: `+20% damage` |

---

### ⚡ Thunder Mace *(MACE)*
| Loại | Mô tả |
|------|-------|
| **Passive** | 10% mỗi đòn → **Sét đánh địch**. Trời mưa: `+50% damage` |
| **Active — Phase 1** | Click 1: tạo **vòng tròn particles** bán kính 5 block, địch trong vòng bị **trói chân (Slowness X, 5s)** |
| **Active — Phase 2** | Click 2: **phóng bản thân lên 20 block**. Khi rơi xuống đánh trúng → địch bị **Darkness 5s** |

---

### 💣 Demo Axe *(AXE)*
| Loại | Mô tả |
|------|-------|
| **Passive** | Mỗi **3 đòn liên tiếp** vào cùng mục tiêu → gắn 💣 **bom hẹn giờ 3s**. Phát nổ gây `12 damage` |
| **Active** | AoE bán kính **15 block**: địch bị **Slowness X (7s)** + vụ nổ trực quan. Đồng đội gần nhận **+10% damage trong 2s** |

---

## 🛠 Lệnh

Quyền: `mythicweapon.admin`

| Lệnh | Mô tả |
|------|-------|
| `/mw give <player> <weaponId>` | Trao vũ khí cho player |
| `/mw list` | Xem danh sách vũ khí đã đăng ký |
| `/mw reload` | Reload config + tự động cập nhật item toàn server |
| `/mw update` | Force cập nhật item MythicWeapon cho toàn bộ player online |

---

## ⚙ Cấu hình

### `weapons.yml`

```yaml
weapons:
  thunder_mace:
    display-name: "&6⚡ &eThunder Mace &6⚡"
    material: MACE
    weapon-type: MACE          # SWORD / AXE / MACE / SPEAR / ROD / SHIELD / BOW / STAFF
    custom-model-data: 0
    nexo-id: ""                # ID Nexo nếu dùng custom texture
    unbreakable: true
    lore:
      - "&7Mô tả..."
    stats:
      damage: 10.0
    enchantments:
      sharpness: 3
    passive-skills:
      - id: lightning_strike
        chance: 10.0
        base-damage: 8.0
        rain-multiplier: 1.5
    active-skill:
      id: thunder_drop
      cooldown: 25
      launch-height: 15.0
```

### Skill passive có sẵn

| ID | Mô tả |
|----|-------|
| `bleed` | Chảy máu theo tick |
| `glow` | Làm địch phát sáng |
| `lightning_strike` | Sét ngẫu nhiên (rain bonus) |
| `storm_combo` | Gây thêm damage + hút food |
| `time_bomb` | Gắn bom hẹn giờ mỗi N đòn |

### Skill active có sẵn

| ID | Mô tả |
|----|-------|
| `dash_strike` | Lao về phía địch + đòn mạnh |
| `speed_buff` | Speed + Jump buff + damage bonus |
| `thunder_drop` | 2-phase: vòng trói + phóng thân |
| `demo_blast` | AoE slowness + nổ + ally buff |

---

## 🔄 Hệ thống Auto-Update

Khi cập nhật `weapons.yml` (thay đổi lore, stats, enchant...), item cũ trong túi player **tự động được cập nhật** mà không cần xoá đi cho lại.

| Thời điểm | Hành động |
|-----------|-----------|
| `/mw reload` | Scan + update toàn bộ player **đang online** |
| Player join server | Update item của player đó sau 1 tick |
| `/mw update` | Manual force update toàn server |

**Phạm vi scan:** Main inventory (36 slot) + Offhand + Armor

---

## 🏗 Kiến trúc

```
MythicWeapon/
├── MythicWeapon-api/          # API, interfaces, data models
│   └── src/main/java/
│       ├── api/skill/         # PassiveSkill, ActiveSkill interfaces
│       ├── api/weapon/        # MythicWeapon, WeaponType
│       └── data/              # PlayerCombatData
├── MythicWeapon-implement/    # Core logic
│   └── src/main/java/
│       ├── core/              # MythicWeaponCore (bootstrap)
│       ├── listener/          # Event listeners
│       ├── manager/           # CooldownManager, ItemManager, ...
│       ├── service/           # WeaponUpdater
│       ├── skill/
│       │   ├── passive/       # BleedSkill, GlowSkill, ...
│       │   └── active/        # DashStrikeSkill, ThunderDropSkill, ...
│       └── weapon/            # WeaponLoader
└── MythicWeapon-plugin/       # Plugin bootstrap + resources
    └── src/main/resources/
        ├── weapons.yml
        ├── messages.yml
        └── plugin.yml
```

### Thêm vũ khí mới

1. Tạo skill class implement `PassiveSkill` / `ActiveSkill`
2. Đăng ký factory trong `MythicWeaponCore.registerSkillFactories()`
3. Thêm weapon entry vào `weapons.yml`
4. `/mw reload`

---

## 📄 License

Internal — **TurtleMC** © 2026
