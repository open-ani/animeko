@REM 这个文件会在 GitHub Actions 的 Windows runner 上执行

@REM 直接用 vcpkg install boost 也可以跑, 但是这会在 CI 上装一小时, 所以就只装了最少的能过编译的包

vcpkg install openssl:x64-windows boost-variant:x64-windows boost-system:x64-windows boost-range:x64-windows boost-crc:x64-windows boost-logic:x64-windows boost-parameter:x64-windows boost-asio:x64-windows boost-variant2:x64-windows boost-multi-index:x64-windows boost-multiprecision:x64-windows
@REM vcpkg install openssl:x64-windows boost:x64-windows

vcpkg install glew:x64-windows

choco install swig -y
choco install openssl -y

vcpkg integrate install
