# Btn-checkhealth

Plugin **kiểm tra - phân tích - đánh giá hiệu suất** server Minecraft nvnmc.

## Kết quả điều tra hiệu năng server (trước khi code)

Đọc qua logs 09–10/10/2026, spark, Paper config, world data:

| Hạng mục | Phát hiện |
|---|---|
| Runtime | Paper 26.2-129 / MC 26.2, **Java 25**, Moonrise, libdeflate |
| World | **527 region files (~3.1 GB)** — rất lớn, nhiều chunk |
| Config | view-distance 6, sim-distance 4 (đã thấp), Paper world config mặc định |
| "Can't keep up" | **0** → TPS không rơi thảm hại liên tục |
| **Nguyên nhân lag chính** | OP `Syrien_Sami` spam **`/fill` 168 lệnh/giây** (13:50:32–37, 14:59, 19:07 ngày 09/10) — tổng ~700 lệnh `/fill`, 8.890 block trong 2 ngày |
| Khác | 20× timeout Mojang sessionserver (do join), DiscordSRV topic updater spam warning (vô hại), EconomyShopGUI 28 sections |

→ Không phải lag do plugin or config; chủ yếu do **spam lệnh nặng trên server thread**. Plugin này giúp phát hiện sớm tình trạng đó.

## Build

```bash
./build.sh        # tải spigot-api 26.2, compile JDK 25, đóng gói jar
```

Ra: `target/Btn-checkhealth-1.0.0.jar`

## Deploy (push lên nvnmc hosting qua SFTP, rồi xóa local)

```bash
./deploy.sh                       # tự lấy jar mới nhất trong target/
./deploy.sh target/Btn-checkhealth-1.0.0.jar
```

Script upload jar → `sftp://sftp.nvnmc.asia:2023` thư mục `plugins/`, rồi xóa local.
Sau khi restart server, gõ `/btn health` để xem báo cáo.

## Lệnh

| Lệnh | Chức năng |
|---|---|
| `/btn health` | Báo cáo đầy đủ: điểm sức khỏe 0–100 + hạng, TPS 1m/5m/15m, MSPT avg/peak, RAM, entities theo loại + theo world, chunks, players, alert |
| `/btn tps` | TPS/MSPT/score nhanh |
| `/btn top [n]` | Top n loại entity đang load |
| `/btn recent [player]` | Tần suất lệnh cmd/s, số lệnh nặng (fill/setblock/clone) theo player |
| `/btn alerts` | Lịch sử cảnh báo |
| `/btn reload` | Reload config |

## Chức năng nổi bật

- **Điểm sức khỏe 0–100** (EXCELLENT/GOOD/FAIR/POOR/CRITICAL) tính từ TPS, MSPT, entity load, RAM, spam lệnh
- **Phát hiện spam lệnh nặng**: theo dõi cmd/s per player, `/fill` `/setblock` `/clone`... cảnh báo console/ops, tùy chọn `action: cancel` để chặn (config)
- **Cảnh báo tự động** khi TPS < 18, MSPT > 45ms, entity > 1500, RAM > 85%
- **Phân tích entity** theo loại (items/mobs/arrows/xp/vehicles) để biết nên dọn gì
- Mẫu mỗi 10 giây (config), tiếc RAM không đáng kể

## Config (`plugins/Btn-checkhealth/config.yml`)

```yaml
monitor:
  interval-seconds: 10
  alert:
    tps-threshold: 18.0
    mspt-threshold-ms: 45.0
    entity-threshold: 1500
    notify-ops: true
    notify-console: true
command-spy:
  enabled: true
  warn-threshold: 25        # cmd/s
  critical-threshold: 60
  action: warn              # warn | cancel
  heavy-commands: [/fill, /setblock, /clone, ...]
```

## SFTP

- Host: sftp.nvnmc.asia | Port: 2023
- User: `vietp8082yvt.b4e77c30` (pass trong deploy.sh)
- Server: Paper 26.2-129 / MC 26.2 / Java 25, port 26059
