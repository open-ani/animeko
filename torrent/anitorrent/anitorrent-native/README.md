# Anitorrent

Anitorrent 是 libtorrent 的一个封装, 专门针对 Ani 使用的场景, 使用 C++ 原生调用 libtorrent, 通过
SWIG 生成 JNI 接口供 Kotlin 对接.

> 为了让 libtorrent 的交互工作都在 C++ 实现, 避免 JNI 交互可能导致的内存所有权等问题. 直接交互也拥有最高的自由度.

> [!WARNING]
> 构建 C++ 不像构建 Kotlin 那样轻松, 这在 Windows 上甚至是***比较有挑战性***的. 提交 PR 后, CI 会
> *承担*一切构建工作.
>
> 此文档仅适用于需要修改 torrent 功能的开发者. 如果你不需要修改 BT 功能代码, 在构建 Ani 测试时也无需使用
> BT 数据源, 则可以直接忽略本文档. Ani 的构建系统默认不会构建 Anitorrent.

## 安装依赖和配置

所有配置项均同时支持通过命令行 `-D` 传递或通过 `local.properties` 传递.
例如配置项 `ani.enable.anitorrent` 可以通过
`-Dani.enable.anitorrent=true` 传递, 或在 `local.properties`
中添加一行 `ani.enable.anitorrent=true`.

### A. macOS (CMake + AppleClang)

macOS 构建操作很简单.

1. 安装 Homebrew, 即 `brew`. 官方安装命令:
   ```shell
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```
2. 在 App Store 中安装 Xcode，并打开一次.
3. 安装 Xcode Commandline Tools:
   ```sheel
   xcode-select --install
   ```
4. 执行安装脚本 [/ci-helper/install-deps-macos.sh](../../../ci-helper/install-deps-macos.sh).
   这将会调用 brew 安装 CMake, Ninja, SWIG, Boost, OpenSSL 等依赖. libtorrent 将会在构建 anitorrent
   时现场构建.
5. 如果你系统没有安装任意种类的大于 17 版本的 JDK, 可以通过 brew 安装:
   ```shell
   brew install openjdk@17
   ```
   如果已经有了, 可以跳过
6. 在项目根目录的 `local.properties` (没有就创建一个) 中添加一行:
   ```properties
   ani.enable.anitorrent=true
   ```
7. 完成. 现在可以运行 `./gradlew :app:desktop:runDistributable` 测试, 或者在 IDE 右上角选择 "Run
   Desktop" 配置.

### B. Windows (Visual Studio 工具链)

Windows 上构建 native 代码是*比较有挑战性*的. 使用 Visual Studio 工具链可以减轻麻烦.

1. 右键开始菜单, 打开 Powershell **管理员模式**, 执行: `Set-ExecutionPolicy Bypass -Scope Process`.
   执行完成后, 窗口不要关闭, 以便后续继续使用.
2. 安装 [Chocolatey](https://chocolatey.org/install). 以下为 Chocolatey 官方的 Powershell
   一键安装命令:
   ```powershell
   Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
   ```
3. 安装 Visual Studio (不是 Visual Studio Code). 确保以下组件勾选:
    - 使用 C++ 的桌面开发
    - 通用 Windows 平台开发
    - 用于 Windows 的 C++ CMake 工具
   - Windows 11 SDK
    - MSVC v___ - VS 2022 C++ x64/x86 生成工具

   安装完成后, 无需启动 VS, 可以直接进行下一步.
4. 安装 [Vcpkg](https://github.com/microsoft/vcpkg).
   建议安装到 C 盘的 `C:/vcpkg` 目录下. 可以使用 Powershell 直接执行以下命令:
    ```powershell
   cd C:\
   git clone https://github.com/microsoft/vcpkg.git
   cd vcpkg
   .\bootstrap-vcpkg.bat
    ```
5. 将 `vcpkg` 的路径添加到系统环境变量 `Path` 中.
6. 重启系统. 重启后, 打开 Powershell **管理员模式**, 确保 `vcpkg` 命令可用. 如果不可用, 说明环境变量配置有误.
7. 在 Powershell 管理员中, 安装 SWIG 和 CMake:
   ```shell
   choco install swig -y
   choco install cmake -y
   ```
8. 执行安装脚本 [/ci-helper/install-deps-windows.cmd](../../../ci-helper/install-deps-windows.cmd).
   不要跳过任何一步.
9. 如果你系统没有正确安装任意种类的大于 17 版本的 JDK (且在环境变量 `JAVA_HOME` 可见), 可以通过
   choco 安装:
   ```shell
   choco install openjdk
   ```
   否则后续构建会找不到 JNI.
10. 获取 `CMAKE_TOOLCHAIN_FILE`:
    ```shell
    vcpkg integrate install
    ```
    该命令将会输出:
    ```text
    Applied user-wide integration for this vcpkg root.
    CMake projects should use: "-DCMAKE_TOOLCHAIN_FILE=D:/vcpkg/scripts/buildsystems/vcpkg.cmake"All MSBuild C++ projects can now #include any installed libraries. Linking will be handled automatically. Installing new libraries will make them instantly available.
    ```
    复制其中的 `CMAKE_TOOLCHAIN_FILE=D:/vcpkg/scripts/buildsystems/vcpkg.cmake`, (注意去除 `-D`),
    添加到 `local.properties` 中.
11. 你最终的 `local.properties` 至少包含以下几行:
    ```properties
    ani.enable.anitorrent=true
    CMAKE_TOOLCHAIN_FILE=D:/vcpkg/scripts/buildsystems/vcpkg.cmake
   
    # 如果提示找不到 CMake, 就添加以下一行手动指定位置
    CMAKE=C\:\\Program Files (x86)\\Microsoft Visual Studio\\2022\\BuildTools\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe
    # 也有可能是以下位置：
    CMAKE=C\:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\Common7\\IDE\\CommonExtensions\\Microsoft\\CMake\\CMake\\bin\\cmake.exe
    ```
12. 完成. 现在可以运行 `./gradlew :app:desktop:runDistributable` 测试, 或者在 IDE 右上角选择 "Run
    Desktop" 配置.

### C. Windows (自定义工具链)

Gradle 默认不会自动构建 Anitorrent. 需要 `ani.enable.anitorrent=true` 才会启用构建.

注意, 非常推荐使用 VS 工具链. 自定义工具链仅限熟悉 C++ 的开发者.

- 所有的路径都会默认从环境变量中寻找, 若找不到时需要手动指定

以下为所有重要配置项:

| 配置项                     | 说明                                 |
|-------------------------|------------------------------------|
| `ani.enable.anitorrent` | 是否启用 Anitorrent 构建. `true`/`false` |
| `CMAKE`                 | cmake 路径                           |
| `NINJA`                 | ninja 路径                           |
| `SWIG`                  | swig 路径                            |
| `CMAKE_C_COMPILER`      | C 编译器的路径                           |
| `CMAKE_CXX_COMPILER`    | C++ 编译器的路径                         |
| `Boost_INCLUDE_DIR`     | Boost 头文件路径. 该路径下需要包含 `boost` 目录   |

一个示例配置 (Windows, 使用 Visual Studio 的 MSVC 编译器, 使用 Choco 安装的 CMAKE, NINJA, SWIG):

```properties
ani.enable.anitorrent=true
CMAKE=cmake
NINJA=C\:\\ProgramData\\chocolatey\\bin\\ninja.exe
SWIG=C\:\\ProgramData\\chocolatey\\bin\\swig.exe
CMAKE_C_COMPILER=D\:\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.37.32822\\bin\\Hostx64\\x64\\cl.exe
CMAKE_CXX_COMPILER=D\:\\Microsoft Visual Studio\\2022\\Community\\VC\\Tools\\MSVC\\14.37.32822\\bin\\Hostx64\\x64\\cl.exe
Boost_INCLUDE_DIR=D\:\\vcpkg\\installed\\x64-windows\\include
```

#### Windows 自定义工具链使用提示

- 使用 x64/amd64 编译器.
  例如: `D:\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.37.32822\bin\Hostx64\x64`. 如果是使用
  CLion, 可在设置中 Toolchain 将 Architecture 设置为 `amd64`.
- 如果提示找不到 `Boost_INCLUDE_DIR`, 可在 CMake 命令行中指定你的 vcpkg 的 include 路径,
  例如: `-DBoost_INCLUDE_DIR=D:\vcpkg\installed\x64-windows\include\`

#### 测试过的工具链

Anitorrent 日常使用如下工具链构建测试:

- macOS AppleClang 14 (Xcode)
- macOS LLVM Clang 18
- Windows MSVC 14 (Visual Studio)

GCC 与 Linux 未经测试.

macOS 支持 aarch64 和 x86_64. Windows 仅支持 x86_64.

## 构建和打包

所有构建工作在 Gradle 自动完成. 有以下几个 task:

- `generateSwig`: 生成 SWIG JNI 接口
- `configureAnitorrent`: 生成 CMake 构建配置
- `buildAnitorrent`: 构建 `libanitorrent.dylib` / `anitorrent.dll`

配置好之后, 在运行 desktop main 时将会自动构建 Anitorrent 并复制到 `appResources` 目录, 详见
task `:app:desktop:copyAnitorrentDylibToResources`. 打包 (如 `:app:desktop:package`) 时也会自动携带.

自动构建能够识别源码变化. 也就是说你可以像编辑 Kotlin 代码一样体验自动构建.

### 构建产物

- macOS: `build-ci/libanitorrent.dylib`
- Windows: `build-ci/anitorrent.dll`
