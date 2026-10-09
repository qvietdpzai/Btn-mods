# Btn-mods

Kho git lưu trữ và build mod (plugin Spigot) cho server nvnmc.

## Build

```bash
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
./deploy.sh libs/SomeMod-1.0.jar
```

## SFTP info

- Host: sftp.nvnmc.asia
- Port: 2023
- User: vietp8082yvt.b4e77c30
- Pass: (see .env / manager)
