# Btn-mods

Kho git lưu trữ & build các mod (plugin Spigot) cho server nvnmc, rồi deploy lên hosting qua SFTP.

Target: **Paper 26.2-129 / Minecraft 26.2 / Java 25**, port 26059, `online-mode=false`.

> **Trạng thái hiện tại (10/10/2026):** Admin đã quyết định **quay về plugin gốc**, nên tất cả Btn-mods
> đã được gỡ khỏi server (jar + folder config). Các plugin gốc đã khôi phục:
> CombatLog, DeathChest, GSit, All-In-One-VeinMiner-TreeFeller, EconomyShopGUI (7.3.2).
> Code trong repo được **giữ lại** để nếu cần dùng lại thì build + deploy tiếp.

## Cấu trúc repo (đa mod)

```
mods/
  Btn-checkhealth/       # kiểm tra / phân tích / đánh giá hiệu suất server
  Btn-combatlog/         # thay CombatLog
  Btn-deathchest/        # thay DeathChest
  Btn-gsit/              # thay GSit
  Btn-managerplayer/     # quản lý người chơi + theo dõi
  Btn-shop/              # thay EconomyShopGUI (nặng, có migrate tool)
  Btn-veinminer/         # thay All-In-One-VeinMiner-TreeFeller
build.sh                 # build tất cả mod hoặc 1 mod: ./build.sh <Tên-mod>
deploy.sh                # push jar lên SFTP plugins/ rồi xóa local
tools/migrate-esg.py     # migrate EconomyShopGUI -> Btn-shop/shops.yml
```

## Build

```bash
./build.sh                          # build tất cả mod trong mods/
./build.sh Btn-managerplayer        # build 1 mod
```

Ra: `target/<Tên-mod>-<version>.jar`. Tự tải `spigot-api 26.2-R0.1-SNAPSHOT` nếu thiếu.

## Deploy (push lên nvnmc hosting qua SFTP, rồi xóa local)

```bash
./deploy.sh target/Btn-managerplayer-1.0.0.jar   # deploy 1 mod
```

Sau khi push **phải restart server** thì plugin mới được nạp.

---

## Btn-checkhealth

Kiểm tra - phân tích - đánh giá hiệu suất server.

**Kết quả điều tra lag (09–10/10/2026):** không có "Can't keep up" (0 lần); nguyên nhân lag chính là OP `Syrien_Sami` spam `/fill` **168 lệnh/giây** (3 đợt: 13:50, 14:59, 19:07 ngày 09/10, ~700 lệnh / 8.890 block). World 527 region files (~3.1 GB).

**Lệnh:** `/btn health` | `tps` | `top [n]` | `recent [player]` | `alerts` | `reload`

Báo cáo gồm: điểm sức khỏe 0–100 + hạng (EXCELLENT→CRITICAL), TPS 1m/5m/15m, MSPT avg/peak, RAM, entity theo loại + theo world, chunks, players, cảnh báo.

Cảnh báo tự động: TPS<18, MSPT>45ms, entities>1500, RAM>85%. Phát hiện spam lệnh nặng `/fill` `/setblock` `/clone`... theo cmd/s per player (`action: warn|cancel`).

## Btn-managerplayer

Quản lý người chơi và theo dõi họ **ở đâu, đang làm gì**.

| Nhóm | Lệnh |
|---|---|
| Xem | `/btnmp list` — online: vị trí, gamemode, hp, ping, playtime, AFK |
| | `/btnmp info <ng>` — chi tiết: UUID, playtime, thống kê block/chết/giết/lệnh, vị trí, block đang nhìn, lệnh/chat/block gần nhất |
| | `/btnmp seen <ng>` — chi tiết người offline |
| | `/btnmp watch <ng>` / `watch off` — theo dõi trực tiếp 15 phút (di chuyển, chat, lệnh, phá/đặt block, chết, giết, mở túi, vào/ra game) |
| | `/btnmp log [n]` — nhật ký hoạt động server gần đây |
| Quản lý | `/btnmp kick\|ban\|unban\|mute\|unmute <ng> [thời gian: 30m\|2h\|3d\|perm] [lý do]` |
| | `/btnmp heal\|feed\|kill\|inv\|ec <ng>` `fly [on\|off]` `gm <survival\|creative\|adventure\|spectator>` |
| | `/btnmp tp <ng>` (tới chỗ họ) · `/btnmp bring <ng>` (kéo về chỗ mình) |
| | `/btnmp bans` \| `mutes` — danh sách đang bị phạt |

Dữ liệu lưu `plugins/Btn-managerplayer/players/<uuid>.yml` (playtime, thống kê, lần cuối). Ban/mute lưu `punishments/*.yml`, **tự hết hạn**, ban chặn ngay lúc login, mute chặn chat.

Quyền: `btnmp.admin` (alias lệnh: `/bm`, `/mplayer`).

---

## Mod thay thế plugin bên ngoài (23 → 19 plugin)

| Btn-mod | Thay thế | Trạng thái |
|---|---|---|
| Btn-combatlog | CombatLog 1.20 | tag combat, chặn lệnh trốn fight, `kill` khi logout giữa combat |
| Btn-deathchest | DeathChest 1.4.0 | đồ vào rương tại chỗ, tự hết hạn, chỉ chủ rương mở |
| Btn-gsit | GSit 3.5.1 | ngồi slab/bậc thang, Shift hoặc `/sit` để đứng |
| Btn-veinminer | All-In-One-VeinMiner-TreeFeller 1.1.5 | phá block cùng loại liền kề, giới hạn theo cuộc, trừ độ bền |

Lệnh: `/combatlog`, `/deathchest`, `/sit`, `/veinminer` (mỗi cái có alias). Cấu hình mỗi mod tại `mods/<tên>/src/main/resources/config.yml`.

Plugin gốc đã được đổi tên thành `*.jar.disabled` trên server → restart là chỉ chạy bản Btn. Muốn hoàn tác: đổi tên lại và xóa jar Btn.

## SFTP

- Host: `sftp.nvnmc.asia` | Port: `2023` | User: `vietp8082yvt.b4e77c30`
- Pass nằm trong `deploy.sh`
