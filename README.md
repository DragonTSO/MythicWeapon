# ⚔ MythicWeapon

> Plugin Minecraft độc lập cung cấp hệ thống vũ khí huyền thoại với skill passive/active hoàn toàn có thể cấu hình.  
> Hỗ trợ **Paper**, **Folia**, và **Canvas**.

---

## 📋 Mục lục
- [Yêu cầu](#-yêu-cầu)
- [Cài đặt](#-cài-đặt)
- [Vũ khí hiện tại](#-vũ-khí-hiện-tại)
- [Lệnh](#-lệnh)
- [Hệ thống hết hạn vũ khí](#-hệ-thống-hết-hạn-vũ-khí)
- [Cấu hình](#-cấu-hình)
- [Hệ thống Auto-Update](#-hệ-thống-auto-update)
- [Kiến trúc](#-kiến-trúc)

---

## 🔧 Yêu cầu

| Yêu cầu | Phiên bản |
|---------|-----------|
| Java | 21+ |
| Minecraft Server | 1.21.x (Paper / Folia / Canvas) |
| Soft Dependency | [Nexo](https://github.com/Nexo) *(tuỳ chọn — custom texture)* |

---

## 📦 Cài đặt

1. Đặt `MythicWeapon.jar` vào thư mục `plugins/`
2. Khởi động server → plugin tự sinh `weapons.yml` và `messages.yml`
3. Cấu hình theo ý muốn
4. `/mw reload` để áp dụng thay đổi

---

## ⚔ Vũ khí hiện tại

### 🗡 Scythe of Blood *(SCYTHE)*
| Loại | Mô tả |
|------|--------|
| **Passive — Chảy Máu** | 30% cơ hội gây **chảy máu 4s** (1.5 damage/giây) |
| **Active — Lướt Đao** | Lướt nhanh 5 block về phía trước. Đòn kế tiếp gây thêm `+5 damage`. Hạ mục tiêu trong 5s → `-50% cooldown` |

---

### 🛡 Guardian's Aegis *(SHIELD)*
| Loại | Mô tả |
|------|--------|
| **Passive — Phản Chiếu** | Block đòn đánh → **phản chiếu 30% damage** lên kẻ tấn công |
| **Active — Khiên Thần** | Stack block liên tiếp. Đạt max → kích hoạt **Regeneration + Speed** trong 5s |

---

### 🎣 Cursed Rod *(ROD)*
| Loại | Mô tả |
|------|--------|
| **Passive — Đánh Cắp Totem** | Câu trúng Player → **đánh cắp Totem** của họ, hồi máu cho người dùng |
| **Active** | Khi đang cooldown → **chặn không cho quăng cần** |

---

### 🔱 Void Spear *(SPEAR)*
| Loại | Mô tả |
|------|--------|
| **Passive — Phát Sáng** | Mỗi đòn có tỉ lệ làm địch **phát sáng (Glowing)** + `+10% damage` |
| **Active — Tốc Chiến** | Nhận **Speed III + Jump II** trong 5s. Đánh trúng địch phát sáng: `+50% damage`. Địch thường: `+20% damage` |

---

### ⚡ Thunder Mace *(MACE)*
| Loại | Mô tả |
|------|--------|
| **Passive — Sét Đánh** | 10% mỗi đòn → **sét đánh địch**. Trời mưa: `+50% damage` |
| **Active — Phase 1** | Click 1: tạo **vòng tròn particles** bán kính 5 block, địch bị **trói chân (Slowness X, 5s)** |
| **Active — Phase 2** | Click 2: **phóng lên 20 block**. Khi rơi đánh trúng → địch bị **Darkness 5s** |

---

### 💣 Explosion Mace *(AXE)*
| Loại | Mô tả |
|------|--------|
| **Passive — Bom Hẹn Giờ** | Mỗi **3 đòn liên tiếp** vào cùng mục tiêu → gắn 💣 bom hẹn giờ 3s, phát nổ gây `12 damage` |
| **Active — AoE Blast** | Bán kính **15 block**: địch bị **Slowness X (7s)** + vụ nổ trực quan (không phá block). Đồng đội gần nhận **+10% damage trong 2s** |

---

### 🩸 Huyết Kiếm *(SWORD)*
| Loại | Mô tả |
|------|--------|
| **Passive — Hút Máu** | Mỗi đòn đánh hồi lại **0.5 tim**. Nếu máu dưới 30% → tăng nhẹ damage |
| **Active — Hiến Tế** | Hiến tế **10% máu hiện tại** để tăng mạnh sát thương trong 10s. Đánh càng đau, hút máu càng nhiều |

---

### 🔥 Rìu Hỏa Ngục *(AXE)*
| Loại | Mô tả |
|------|--------|
| **Passive — Thiêu Xương** | Đòn đánh **đốt cháy** mục tiêu. Càng thấp máu, càng cháy đau |
| **Active — Cuồng Hỏa (10s)** | Đánh vào mục tiêu đang cháy → **tăng tốc chạy + damage**, stack tối đa 3 lần |

---

### 🌊 Đinh Ba Bão Tố *(TRIDENT)*
| Loại | Mô tả |
|------|--------|
| **Passive — Bão Tố** | Khi ở dưới nước/trời mưa: **+30% damage + Speed II**. 30% tỉ lệ **triệu hồi sét** (+6 damage) + Glowing |
| **Active — Hoán Đổi** | Ném trident trúng mục tiêu → **đổi vị trí** player ↔ target. Tạo **vụ nổ** tại vị trí cũ + **3 tia sét** liên tiếp đánh vào mục tiêu |

---

## 🛠 Lệnh

Quyền: `mythicweapon.admin`

| Lệnh | Mô tả |
|------|--------|
| `/mw give <player> <weaponId>` | Trao vũ khí **vĩnh viễn** cho player |
| `/mw give <player> <weaponId> <thời_hạn>` | Trao vũ khí **có thời hạn** (VD: `2d`, `12h`, `30m`, `1d12h`) |
| `/mw list` | Xem danh sách vũ khí đã đăng ký |
| `/mw reload` | Reload config + tự động cập nhật item toàn server |
| `/mw update` | Force cập nhật item MythicWeapon cho toàn bộ player online |

---

## ⏳ Hệ thống hết hạn vũ khí

Cho phép admin trao vũ khí **có thời hạn**. Khi hết hạn, vũ khí tự động **biến mất** khỏi túi đồ player.

### Cú pháp thời hạn

| Định dạng | Ý nghĩa |
|-----------|---------|
| `2d` | 2 ngày |
| `12h` | 12 giờ |
| `30m` | 30 phút |
| `1d12h30m` | 1 ngày 12 giờ 30 phút |

### Cách hoạt động

1. **Khi trao vũ khí:** Timestamp hết hạn lưu vào `PersistentDataContainer` + hiển thị dòng lore `⏳ Hết hạn: X ngày Y giờ`
2. **Mỗi 60 giây:** `ExpiryTask` quét inventory tất cả player online
3. **Khi hết hạn:** Vũ khí bị xóa + hiệu ứng khói + âm thanh vỡ + thông báo chat

### Ví dụ

```bash
# Trao Storm Trident hết hạn sau 7 ngày
/mw give Steve storm_trident 7d

# Trao Blood Sword hết hạn sau 12 giờ
/mw give Steve blood_sword 12h

# Trao vĩnh viễn (không có thời hạn)
/mw give Steve thunder_mace
```

---

## ⚙ Cấu hình

### `weapons.yml`

```yaml
weapons:
  thunder_mace:
    display-name: "&6⚡ &eThunder Mace &6⚡"
    material: MACE
    weapon-type: MACE          # SWORD / AXE / MACE / SPEAR / ROD / SHIELD / SCYTHE / TRIDENT
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
|----|--------|
| `bleed` | Chảy máu theo tick |
| `glow` | Làm địch phát sáng |
| `lightning_strike` | Sét ngẫu nhiên (rain bonus) |
| `storm_combo` | Gây thêm damage + hút food |
| `time_bomb` | Gắn bom hẹn giờ mỗi N đòn |
| `blood_lifesteal` | Hút máu mỗi đòn đánh |
| `burn` | Đốt cháy mục tiêu |
| `trident_storm` | Buff nước/mưa + sét |

### Skill active có sẵn

| ID | Mô tả |
|----|--------|
| `dash_strike` | Lao về phía địch + đòn mạnh |
| `speed_buff` | Speed + Jump buff + damage bonus |
| `thunder_drop` | 2-phase: vòng trói + phóng thân |
| `thunder_launch` | Phóng thân + hiệu ứng hạ cánh |
| `demo_blast` | AoE slowness + nổ + ally buff |
| `blood_sacrifice` | Hiến tế máu → tăng damage |
| `inferno_rage` | Cuồng hỏa → stack damage + speed |
| `trident_swap` | Ném trident → đổi vị trí + nổ + sét |

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
│       ├── listener/          # Event listeners (Combat, Shield, Trident, ...)
│       ├── manager/           # CooldownManager, ItemManager, CombatDataManager
│       ├── service/           # WeaponUpdater, ExpiryTask
│       ├── skill/
│       │   ├── passive/       # BleedSkill, BurnSkill, TridentStormPassive, ...
│       │   └── active/        # DashStrikeSkill, TridentSwapSkill, ...
│       ├── util/              # SchedulerUtil, ItemUtil, MessageUtil
│       └── config/            # WeaponConfigLoader, MessageConfig
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

### Folia / Canvas Compatibility

Plugin hoàn toàn tương thích với **Folia** và **Canvas** (các fork đa luồng):
- Sử dụng `SchedulerUtil` để tự động chọn đúng scheduler (Entity/Global/Region)
- Teleport qua `teleportAsync` trên Folia (qua reflection)
- Không dùng `BukkitRunnable` trực tiếp — luôn qua `SchedulerUtil`

---

## 📄 License

Internal — **TurtleMC** © 2026
