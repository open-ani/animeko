# ani-update

Animeko 的 Windows 更新器, 应用退出后把新版本换进安装目录. 行为和参数见 `src/main.rs` 顶部的说明.

## 构建

应用打包时用的是提交在 `app/desktop/appResources/windows/ani_update.exe` 的二进制 (x86_64, Windows on ARM 上以模拟方式运行).
改了源码后:

1. 跑 GitHub Actions 的 "Build ani_update" workflow (改到 `tools/ani-update/` 的 PR 会自动跑), 下载 `ani_update` artifact;
2. 用它替换 `app/desktop/appResources/windows/ani_update.exe`, 与源码改动一起提交.

本地构建: `cargo build --release`, 产物在 `target/release/ani_update.exe`. 在 macOS 或 Linux 上可以 `cargo test` 和
`cargo check --target x86_64-pc-windows-msvc` 做类型检查, 但 Windows API 调用需要在 Windows 上运行.
