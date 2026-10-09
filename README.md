# Btn-mods

Kho git lưu trữ và build mod (plugin Spigot) cho server nvnmc.

## Build

```bash
./build.sh           # khuyến nghị: tự tải spigot-api + compile + đóng gói
# hoặc dùng Maven (nếu môi trường có SSL/Maven Central truy cập được):
mvn package
```

Jar ra: `target/Btn-mods-1.0.0.jar`

## Deploy (push lên nvnmc hosting qua SFTP, rồi xóa local)

```bash
./deploy.sh                      # tự lấy jar mới nhất trong target/
./deploy.sh target/Btn-mods-1.0.0.jar
```

Script sẽ:
1. Upload jar lên `sftp://sftp.nvnmc.asia:2023` -> thư mục `plugins/`
2. Xóa jar ở local sau khi push xong

## Tải mod ngoài về

Nếu dùng mod ngoài (không tự code), tải jar vào `libs/` rồi deploy:

```bash
# ví dụ: tải mod ngoài về libs/
curl -skL -o libs/SomeMod-1.0.jar "<url-mod>"
./deploy.sh libs/SomeMod-1.0.jar
```

Hoặc deploy trực tiếp 1 file jar bất kỳ:

```bash
./deploy.sh /path/to/Mod.jar
```

## SFTP info

- Host: sftp.nvnmc.asia
- Port: 2023
- User: vietp8082yvt.b4e77c30
- Pass: (see .env / manager)
