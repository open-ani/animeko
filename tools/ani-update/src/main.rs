// Copyright (C) 2024-2026 OpenAni and contributors.
//
// 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
// Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
//
// https://github.com/open-ani/ani/blob/main/LICENSE

//! Animeko 的 Windows 更新器. 应用在退出前启动它, 它等应用退出后把新版本换进安装目录, 再启动新版本.
//!
//! 新版本有两种来源:
//! - `--staging <dir>`: 应用已经拼好的完整新版本目录 (增量更新). 与安装目录在同一卷时用两次重命名整体
//!   交换, 第二次重命名失败就把旧版本换回去; 不在同一卷时逐文件移动, 并删掉 `app/` 和 `runtime/` 下
//!   新版本没有的文件.
//! - `--archive <zip>`: 完整发布包. 先解压到安装目录旁边的暂存目录, 再按上面的方式交换.
//!
//! 更新器必须从安装目录之外运行 (应用把它复制到临时目录), 否则安装目录无法重命名.
//! 成功时直接退出, 控制台窗口随之关闭; 失败时把原因留在窗口里等用户按回车, 日志同时写到
//! `%TEMP%\ani-update.log`.

use std::collections::HashSet;
use std::ffi::OsString;
use std::fs::{self, File};
use std::io::{self, Write};
use std::path::{Component, Path, PathBuf, Prefix};
use std::process::Command;
use std::time::{Duration, Instant};

const APP_EXECUTABLE: &str = "Ani.exe";
/// 应用退出后文件句柄释放、杀毒软件扫描都可能让重命名短暂失败, 在这段时间内重试.
const RENAME_RETRY: Duration = Duration::from_secs(60);
const RETRY_INTERVAL: Duration = Duration::from_millis(500);
/// 应用的进程退出后, 等安装目录下再没有任何进程在运行 (启动器可能另起进程, JVM 退出也要一点时间) 的最长时间.
const PROCESS_EXIT_WAIT: Duration = Duration::from_secs(120);
/// 逐文件移动时只清理这两个目录里的旧文件, 安装目录根下用户自己放的文件不动.
const MANAGED_DIRS: [&str; 2] = ["app", "runtime"];

fn main() {
    let mut log = Log::open();
    log.info(msg::STARTED);

    let args = match Args::parse(std::env::args_os().skip(1)) {
        Ok(args) => args,
        Err(error) => {
            log.error(&error);
            log.info(msg::USAGE);
            pause();
            std::process::exit(2);
        }
    };

    if let Err(error) = run(&args, &mut log) {
        log.error(&error);
        pause();
        std::process::exit(1);
    }

    if args.launch {
        log.info(msg::LAUNCHING);
        if let Err(error) = launch(&args.app_dir) {
            log.error(format!("{}: {}", msg::LAUNCH_FAILED, error));
            pause();
            std::process::exit(1);
        }
    }
    log.info(msg::DONE);
}

fn run(args: &Args, log: &mut Log) -> Result<(), String> {
    if let Some(pid) = args.pid {
        log.info(format!("{} (pid {})", msg::WAITING_EXIT, pid));
        wait_for_exit(pid, log);
    }
    wait_for_app_processes(&args.app_dir, log);

    let staging = match &args.source {
        Source::Staging(dir) => dir.clone(),
        Source::Archive(archive) => {
            let staging = unique_sibling(&args.app_dir, ".staging");
            log.info(format!(
                "{} {} -> {}",
                msg::EXTRACTING,
                archive.display(),
                staging.display()
            ));
            extract_archive(archive, &staging)
                .map_err(|e| format!("{}: {}", msg::EXTRACT_FAILED, e))?;
            staging
        }
    };

    if !staging.join(APP_EXECUTABLE).is_file() {
        return Err(format!("{}: {}", msg::INVALID_STAGING, staging.display()));
    }

    replace_app(&args.app_dir, &staging, log)
}

/// 把 [`staging`] 换到 [`app_dir`] 的位置.
fn replace_app(app_dir: &Path, staging: &Path, log: &mut Log) -> Result<(), String> {
    if same_volume(app_dir, staging) {
        let old_dir = unique_sibling(app_dir, ".old");
        log.info(format!(
            "{} {} -> {}",
            msg::SWAPPING,
            app_dir.display(),
            old_dir.display()
        ));
        match retry(RENAME_RETRY, || fs::rename(app_dir, &old_dir)) {
            Ok(()) => {
                if let Err(error) = retry(RENAME_RETRY, || fs::rename(staging, app_dir)) {
                    // 第二步失败就把旧版本换回去, 用户至少还能继续用旧版本.
                    let rollback = fs::rename(&old_dir, app_dir);
                    return Err(format!(
                        "{}: {} (rollback: {:?})",
                        msg::SWAP_FAILED,
                        error,
                        rollback.map(|_| "ok")
                    ));
                }
                if let Err(error) = fs::remove_dir_all(&old_dir) {
                    log.warn(format!(
                        "{} {}: {}{}",
                        msg::OLD_LEFTOVER,
                        old_dir.display(),
                        error,
                        lockers_note(&files_in(&old_dir))
                    ));
                }
                log.info(msg::SWAPPED);
                return Ok(());
            }
            Err(error) => {
                log.warn(format!(
                    "{}: {}{}",
                    msg::SWAP_UNAVAILABLE,
                    error,
                    lockers_note(&files_in(app_dir))
                ));
            }
        }
    } else {
        log.info(msg::DIFFERENT_VOLUME);
    }
    move_files(app_dir, staging, log)
}

/// 逐文件把 [`staging`] 移进 [`app_dir`], 再删掉 [`MANAGED_DIRS`] 里新版本没有的文件.
fn move_files(app_dir: &Path, staging: &Path, log: &mut Log) -> Result<(), String> {
    log.info(format!(
        "{} {} -> {}",
        msg::MOVING,
        staging.display(),
        app_dir.display()
    ));
    let mut new_files = HashSet::new();
    for path in walk(staging).map_err(|e| e.to_string())? {
        let relative = path
            .strip_prefix(staging)
            .map_err(|e| e.to_string())?
            .to_path_buf();
        let target = app_dir.join(&relative);
        if path.is_dir() {
            fs::create_dir_all(&target)
                .map_err(|e| format!("{} {}: {}", msg::MOVE_FAILED, target.display(), e))?;
            continue;
        }
        if let Some(parent) = target.parent() {
            fs::create_dir_all(parent)
                .map_err(|e| format!("{} {}: {}", msg::MOVE_FAILED, target.display(), e))?;
        }
        retry(RENAME_RETRY, || replace_file(&path, &target)).map_err(|e| {
            format!(
                "{} {}: {}{}",
                msg::MOVE_FAILED,
                target.display(),
                e,
                lockers_note(std::slice::from_ref(&target))
            )
        })?;
        new_files.insert(relative);
    }

    for dir in MANAGED_DIRS {
        let managed = app_dir.join(dir);
        if !managed.is_dir() {
            continue;
        }
        for path in walk(&managed).map_err(|e| e.to_string())? {
            if path.is_dir() {
                continue;
            }
            let relative = match path.strip_prefix(app_dir) {
                Ok(relative) => relative,
                Err(_) => continue,
            };
            if !new_files.contains(relative) {
                if let Err(error) = retry(RENAME_RETRY, || remove_file(&path)) {
                    log.warn(format!(
                        "{} {}: {}",
                        msg::STALE_LEFTOVER,
                        path.display(),
                        error
                    ));
                }
            }
        }
    }

    if let Err(error) = fs::remove_dir_all(staging) {
        log.warn(format!(
            "{} {}: {}",
            msg::OLD_LEFTOVER,
            staging.display(),
            error
        ));
    }
    log.info(msg::MOVED);
    Ok(())
}

/// 用 [`source`] 替换 [`target`]. 同一卷上是重命名, 否则复制后删除源文件.
fn replace_file(source: &Path, target: &Path) -> io::Result<()> {
    if target.exists() {
        remove_file(target)?;
    }
    match fs::rename(source, target) {
        Ok(()) => Ok(()),
        Err(_) => {
            fs::copy(source, target)?;
            fs::remove_file(source)
        }
    }
}

/// 删除文件, 只读属性会先去掉: 解压工具可能把发布包里的文件标成只读.
fn remove_file(path: &Path) -> io::Result<()> {
    match fs::remove_file(path) {
        Ok(()) => Ok(()),
        Err(error) if error.kind() == io::ErrorKind::PermissionDenied => {
            let mut permissions = fs::metadata(path)?.permissions();
            #[allow(clippy::permissions_set_readonly_false)]
            permissions.set_readonly(false);
            fs::set_permissions(path, permissions)?;
            fs::remove_file(path)
        }
        Err(error) => Err(error),
    }
}

/// 把发布包解压到 [`staging`]. 发布包里的文件都在顶层的 `Ani/` 目录下, 解压时去掉这一层.
fn extract_archive(archive: &Path, staging: &Path) -> Result<(), String> {
    let file = File::open(archive).map_err(|e| e.to_string())?;
    let mut zip = zip::ZipArchive::new(file).map_err(|e| e.to_string())?;
    let root = common_root(&zip);
    fs::create_dir_all(staging).map_err(|e| e.to_string())?;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index).map_err(|e| e.to_string())?;
        let Some(enclosed) = entry.enclosed_name() else {
            return Err(format!("unsafe entry name: {}", entry.name()));
        };
        let relative = match &root {
            Some(root) => match enclosed.strip_prefix(root) {
                Ok(stripped) => stripped.to_path_buf(),
                Err(_) => enclosed,
            },
            None => enclosed,
        };
        if relative.as_os_str().is_empty() {
            continue;
        }
        let target = staging.join(relative);
        if entry.is_dir() {
            fs::create_dir_all(&target).map_err(|e| e.to_string())?;
            continue;
        }
        if let Some(parent) = target.parent() {
            fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let mut output =
            File::create(&target).map_err(|e| format!("{}: {}", target.display(), e))?;
        io::copy(&mut entry, &mut output).map_err(|e| format!("{}: {}", target.display(), e))?;
    }
    Ok(())
}

/// 所有 entry 共同的顶层目录, 没有则为 `None`.
fn common_root<R: io::Read + io::Seek>(zip: &zip::ZipArchive<R>) -> Option<PathBuf> {
    let mut root: Option<String> = None;
    for name in zip.file_names() {
        let first = name.split('/').next()?.to_string();
        if first.is_empty() || name.find('/').is_none() && !name.ends_with('/') {
            return None;
        }
        match &root {
            Some(existing) if *existing != first => return None,
            Some(_) => {}
            None => root = Some(first),
        }
    }
    root.map(PathBuf::from)
}

/// 两个路径是否在同一卷上, 决定能否用重命名交换. 判断不了时按不同卷处理, 走逐文件移动.
fn same_volume(a: &Path, b: &Path) -> bool {
    fn prefix(path: &Path) -> Option<String> {
        let canonical = fs::canonicalize(path).ok()?;
        match canonical.components().next()? {
            Component::Prefix(prefix) => Some(match prefix.kind() {
                Prefix::VerbatimDisk(disk) | Prefix::Disk(disk) => {
                    format!("disk:{}", disk.to_ascii_uppercase())
                }
                Prefix::VerbatimUNC(server, share) | Prefix::UNC(server, share) => {
                    format!(
                        "unc:{}/{}",
                        server.to_string_lossy(),
                        share.to_string_lossy()
                    )
                }
                _ => return None,
            }),
            _ => None,
        }
    }
    match (prefix(a), prefix(b)) {
        (Some(a), Some(b)) => a == b,
        _ => false,
    }
}

/// `app_dir` 旁边一个不存在的路径, 名字是目录名加 [`suffix`], 已存在就再加序号.
fn unique_sibling(app_dir: &Path, suffix: &str) -> PathBuf {
    let name = app_dir
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_else(|| "Ani".to_string());
    let parent = app_dir
        .parent()
        .map(Path::to_path_buf)
        .unwrap_or_else(|| PathBuf::from("."));
    let mut candidate = parent.join(format!("{name}{suffix}"));
    let mut counter = 1;
    while candidate.exists() {
        candidate = parent.join(format!("{name}{suffix}{counter}"));
        counter += 1;
    }
    candidate
}

fn walk(dir: &Path) -> io::Result<Vec<PathBuf>> {
    let mut result = Vec::new();
    let mut pending = vec![dir.to_path_buf()];
    while let Some(current) = pending.pop() {
        for entry in fs::read_dir(&current)? {
            let path = entry?.path();
            if path.is_dir() {
                pending.push(path.clone());
            }
            result.push(path);
        }
    }
    Ok(result)
}

fn retry<T>(timeout: Duration, mut action: impl FnMut() -> io::Result<T>) -> io::Result<T> {
    let deadline = Instant::now() + timeout;
    loop {
        match action() {
            Ok(value) => return Ok(value),
            Err(error) if Instant::now() < deadline => {
                let _ = error;
                std::thread::sleep(RETRY_INTERVAL);
            }
            Err(error) => return Err(error),
        }
    }
}

/// 等 pid 对应的进程退出. 进程已经不在时直接返回; 打不开进程时记下原因, 之后还有 [`wait_for_app_processes`] 兜底.
#[cfg(windows)]
fn wait_for_exit(pid: u32, log: &mut Log) {
    use windows_sys::Win32::Foundation::{CloseHandle, GetLastError};
    use windows_sys::Win32::System::Threading::{
        OpenProcess, WaitForSingleObject, INFINITE, PROCESS_SYNCHRONIZE,
    };
    // SAFETY: 只用句柄等待, 等完就关闭.
    unsafe {
        let handle = OpenProcess(PROCESS_SYNCHRONIZE, 0, pid);
        if handle.is_null() {
            log.warn(format!(
                "{} (pid {}, error {})",
                msg::OPEN_PROCESS_FAILED,
                pid,
                GetLastError()
            ));
            return;
        }
        WaitForSingleObject(handle, INFINITE);
        CloseHandle(handle);
    }
}

/// 更新器只在 Windows 上发布; 其他平台只用来跑测试.
#[cfg(not(windows))]
fn wait_for_exit(_pid: u32, _log: &mut Log) {}

/// 等到没有进程从 [`app_dir`] 里运行. 应用的主进程退出后, 它的子进程和尚未完成退出的 JVM 仍可能占着安装目录里的文件,
/// 这时重命名目录和替换文件都会失败. 超过 [`PROCESS_EXIT_WAIT`] 只记日志, 后面的文件操作自己还会重试.
fn wait_for_app_processes(app_dir: &Path, log: &mut Log) {
    let start = Instant::now();
    let mut reported = false;
    loop {
        let running = running_processes_under(app_dir);
        if running.is_empty() {
            return;
        }
        if !reported {
            log.info(format!(
                "{}: {}",
                msg::WAITING_PROCESSES,
                describe_processes(&running)
            ));
            reported = true;
        }
        if start.elapsed() > PROCESS_EXIT_WAIT {
            log.warn(format!(
                "{}: {}",
                msg::PROCESSES_STILL_RUNNING,
                describe_processes(&running)
            ));
            return;
        }
        std::thread::sleep(RETRY_INTERVAL);
    }
}

/// 文件操作失败时附在日志里的说明: 哪些进程占着这些文件 (按 Restart Manager 的记录). 没有时为空.
fn lockers_note(files: &[PathBuf]) -> String {
    let lockers = locking_processes(files);
    if lockers.is_empty() {
        String::new()
    } else {
        format!(" ({}: {})", msg::LOCKED_BY, describe_processes(&lockers))
    }
}

/// [`dir`] 下的全部文件 (不含目录), 查占用时用. 读不了目录就当作没有文件.
fn files_in(dir: &Path) -> Vec<PathBuf> {
    walk(dir)
        .map(|paths| paths.into_iter().filter(|p| p.is_file()).collect())
        .unwrap_or_default()
}

fn describe_processes(processes: &[(u32, PathBuf)]) -> String {
    processes
        .iter()
        .map(|(pid, path)| format!("{} (pid {})", path.display(), pid))
        .collect::<Vec<_>>()
        .join(", ")
}

/// 可执行文件在 [`dir`] 下的进程 (不含自己) 的 pid 和路径.
#[cfg(windows)]
fn running_processes_under(dir: &Path) -> Vec<(u32, PathBuf)> {
    use windows_sys::Win32::Foundation::{CloseHandle, INVALID_HANDLE_VALUE};
    use windows_sys::Win32::System::Diagnostics::ToolHelp::{
        CreateToolhelp32Snapshot, Process32FirstW, Process32NextW, PROCESSENTRY32W,
        TH32CS_SNAPPROCESS,
    };

    let own_pid = std::process::id();
    let mut result = Vec::new();
    // SAFETY: 快照用完关闭; PROCESSENTRY32W 在调用前填好 dwSize.
    unsafe {
        let snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if snapshot == INVALID_HANDLE_VALUE {
            return result;
        }
        let mut entry: PROCESSENTRY32W = std::mem::zeroed();
        entry.dwSize = std::mem::size_of::<PROCESSENTRY32W>() as u32;
        if Process32FirstW(snapshot, &mut entry) != 0 {
            loop {
                let pid = entry.th32ProcessID;
                if pid != 0 && pid != own_pid {
                    if let Some(path) = process_image(pid) {
                        if is_under(&path, dir) {
                            result.push((pid, path));
                        }
                    }
                }
                if Process32NextW(snapshot, &mut entry) == 0 {
                    break;
                }
            }
        }
        CloseHandle(snapshot);
    }
    result
}

#[cfg(not(windows))]
fn running_processes_under(_dir: &Path) -> Vec<(u32, PathBuf)> {
    Vec::new()
}

/// 用 Restart Manager 查出占着 [`files`] 中任一文件的进程, 给出 pid 和可执行文件路径 (拿不到路径时是 Restart Manager
/// 记录的名字). 只用来写日志, 查不到就返回空.
#[cfg(windows)]
fn locking_processes(files: &[PathBuf]) -> Vec<(u32, PathBuf)> {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Foundation::ERROR_MORE_DATA;
    use windows_sys::Win32::System::RestartManager::{
        RmEndSession, RmGetList, RmRegisterResources, RmStartSession, CCH_RM_SESSION_KEY,
        RM_PROCESS_INFO,
    };

    let mut result = Vec::new();
    if files.is_empty() {
        return result;
    }
    let names: Vec<Vec<u16>> = files
        .iter()
        .map(|path| path.as_os_str().encode_wide().chain(Some(0)).collect())
        .collect();
    let pointers: Vec<*const u16> = names.iter().map(|name| name.as_ptr()).collect();
    let mut session = 0u32;
    let mut key = [0u16; CCH_RM_SESSION_KEY as usize + 1];
    // SAFETY: 会话用完就结束; 传给系统调用的数组和长度一致; RM_PROCESS_INFO 由系统填充.
    unsafe {
        if RmStartSession(&mut session, 0, key.as_mut_ptr()) != 0 {
            return result;
        }
        if RmRegisterResources(
            session,
            pointers.len() as u32,
            pointers.as_ptr(),
            0,
            std::ptr::null(),
            0,
            std::ptr::null(),
        ) == 0
        {
            let mut needed = 0u32;
            let mut count = 0u32;
            let mut reasons = 0u32;
            let status = RmGetList(
                session,
                &mut needed,
                &mut count,
                std::ptr::null_mut(),
                &mut reasons,
            );
            if (status == 0 || status == ERROR_MORE_DATA) && needed > 0 {
                let mut infos: Vec<RM_PROCESS_INFO> = vec![std::mem::zeroed(); needed as usize];
                count = needed;
                if RmGetList(
                    session,
                    &mut needed,
                    &mut count,
                    infos.as_mut_ptr(),
                    &mut reasons,
                ) == 0
                {
                    for info in infos.iter().take(count as usize) {
                        let pid = info.Process.dwProcessId;
                        let name_len = info
                            .strAppName
                            .iter()
                            .position(|&c| c == 0)
                            .unwrap_or(info.strAppName.len());
                        let name = String::from_utf16_lossy(&info.strAppName[..name_len]);
                        result.push((
                            pid,
                            process_image(pid).unwrap_or_else(|| PathBuf::from(name)),
                        ));
                    }
                }
            }
        }
        RmEndSession(session);
    }
    result
}

#[cfg(not(windows))]
fn locking_processes(_files: &[PathBuf]) -> Vec<(u32, PathBuf)> {
    Vec::new()
}

/// 进程可执行文件的完整路径, 打不开进程时为 `None`.
#[cfg(windows)]
fn process_image(pid: u32) -> Option<PathBuf> {
    use std::os::windows::ffi::OsStringExt;
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Threading::{
        OpenProcess, QueryFullProcessImageNameW, PROCESS_NAME_WIN32,
        PROCESS_QUERY_LIMITED_INFORMATION,
    };
    // SAFETY: 句柄用完关闭; 缓冲区长度传给了系统调用.
    unsafe {
        let handle = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid);
        if handle.is_null() {
            return None;
        }
        let mut buffer = vec![0u16; 32 * 1024];
        let mut length = buffer.len() as u32;
        let ok = QueryFullProcessImageNameW(
            handle,
            PROCESS_NAME_WIN32,
            buffer.as_mut_ptr(),
            &mut length,
        ) != 0;
        CloseHandle(handle);
        ok.then(|| PathBuf::from(OsString::from_wide(&buffer[..length as usize])))
    }
}

/// [`path`] 是否在 [`dir`] 下. Windows 路径不分大小写, 可能带 `\\?\` 前缀.
fn is_under(path: &Path, dir: &Path) -> bool {
    fn normalize(path: &Path) -> String {
        let text = path.to_string_lossy().replace('/', "\\");
        let text = text.strip_prefix("\\\\?\\").unwrap_or(&text);
        text.trim_end_matches('\\').to_lowercase()
    }
    let path = normalize(path);
    let dir = normalize(dir);
    path.len() > dir.len() && path.starts_with(&dir) && path.as_bytes()[dir.len()] == b'\\'
}

fn launch(app_dir: &Path) -> io::Result<()> {
    Command::new(app_dir.join(APP_EXECUTABLE))
        .current_dir(app_dir)
        .spawn()
        .map(|_| ())
}

fn pause() {
    println!("{}", msg::PRESS_ENTER);
    let mut line = String::new();
    let _ = io::stdin().read_line(&mut line);
}

struct Args {
    pid: Option<u32>,
    app_dir: PathBuf,
    source: Source,
    launch: bool,
}

enum Source {
    Staging(PathBuf),
    Archive(PathBuf),
}

impl Args {
    fn parse(args: impl Iterator<Item = OsString>) -> Result<Args, String> {
        let mut pid = None;
        let mut app_dir = None;
        let mut source = None;
        let mut launch = true;
        let mut args = args.peekable();
        while let Some(arg) = args.next() {
            let arg = arg.to_string_lossy().into_owned();
            let mut value = |name: &str| -> Result<OsString, String> {
                args.next()
                    .ok_or_else(|| format!("{} {}", msg::MISSING_VALUE, name))
            };
            match arg.as_str() {
                "--pid" => {
                    let raw = value("--pid")?;
                    pid = Some(
                        raw.to_string_lossy()
                            .parse::<u32>()
                            .map_err(|_| format!("{} --pid", msg::INVALID_VALUE))?,
                    );
                }
                "--app-dir" => app_dir = Some(PathBuf::from(value("--app-dir")?)),
                "--staging" => source = Some(Source::Staging(PathBuf::from(value("--staging")?))),
                "--archive" => source = Some(Source::Archive(PathBuf::from(value("--archive")?))),
                "--no-launch" => launch = false,
                other => return Err(format!("{} {}", msg::UNKNOWN_ARGUMENT, other)),
            }
        }
        let app_dir = app_dir.ok_or_else(|| format!("{} --app-dir", msg::MISSING_ARGUMENT))?;
        let source =
            source.ok_or_else(|| format!("{} --staging / --archive", msg::MISSING_ARGUMENT))?;
        Ok(Args {
            pid,
            app_dir,
            source,
            launch,
        })
    }
}

/// 同时写到控制台和 `%TEMP%\ani-update.log`.
struct Log {
    file: Option<File>,
}

impl Log {
    fn open() -> Log {
        let path = std::env::temp_dir().join("ani-update.log");
        let file = fs::OpenOptions::new()
            .create(true)
            .append(true)
            .open(path)
            .ok();
        Log { file }
    }

    fn info(&mut self, message: impl std::fmt::Display) {
        self.write("INFO", message);
    }

    fn warn(&mut self, message: impl std::fmt::Display) {
        self.write("WARN", message);
    }

    fn error(&mut self, message: impl std::fmt::Display) {
        self.write("ERROR", message);
    }

    fn write(&mut self, level: &str, message: impl std::fmt::Display) {
        let line = format!("[{level}] {message}");
        println!("{line}");
        if let Some(file) = &mut self.file {
            let _ = writeln!(file, "{} {line}", unix_time());
        }
    }
}

fn unix_time() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// 用户界面语言是中文时用中文, 其他一律英文.
mod msg {
    pub struct Message {
        en: &'static str,
        zh: &'static str,
    }

    impl Message {
        pub const fn new(en: &'static str, zh: &'static str) -> Message {
            Message { en, zh }
        }
    }

    impl std::fmt::Display for Message {
        fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            f.write_str(if super::is_chinese() {
                self.zh
            } else {
                self.en
            })
        }
    }

    macro_rules! define {
        ($($name:ident => $en:literal, $zh:literal;)*) => {
            $(pub const $name: Message = Message::new($en, $zh);)*
        };
    }

    define! {
        STARTED => "Animeko updater started", "Animeko 更新器已启动";
        USAGE => "Usage: ani_update --app-dir <dir> (--staging <dir> | --archive <zip>) [--pid <pid>] [--no-launch]",
            "用法: ani_update --app-dir <安装目录> (--staging <新版本目录> | --archive <zip>) [--pid <进程 id>] [--no-launch]";
        WAITING_EXIT => "Waiting for Animeko to exit", "等待 Animeko 退出";
        OPEN_PROCESS_FAILED => "Cannot open the process, not waiting for it", "无法打开进程, 不等待它退出";
        WAITING_PROCESSES => "Waiting for processes still running from the installation directory",
            "等待仍在安装目录中运行的进程退出";
        PROCESSES_STILL_RUNNING => "Processes still running from the installation directory",
            "仍在安装目录中运行的进程";
        LOCKED_BY => "In use by", "被以下进程占用";
        EXTRACTING => "Extracting", "正在解压";
        EXTRACT_FAILED => "Failed to extract the package", "解压安装包失败";
        INVALID_STAGING => "The new version directory does not contain Ani.exe", "新版本目录里没有 Ani.exe";
        SWAPPING => "Swapping the installation directory", "交换安装目录";
        SWAPPED => "Installation directory replaced", "安装目录已替换";
        SWAP_FAILED => "Failed to move the new version into place, the old version was kept",
            "新版本移动到安装目录失败, 已保留旧版本";
        SWAP_UNAVAILABLE => "Cannot rename the installation directory, moving files one by one instead",
            "无法重命名安装目录, 改为逐文件移动";
        DIFFERENT_VOLUME => "The new version is on a different volume, moving files one by one",
            "新版本与安装目录不在同一卷上, 逐文件移动";
        MOVING => "Moving files", "正在移动文件";
        MOVED => "Files replaced", "文件已替换";
        MOVE_FAILED => "Failed to replace", "无法替换";
        STALE_LEFTOVER => "Could not remove an old file", "无法删除旧文件";
        OLD_LEFTOVER => "Could not remove the leftover directory, it can be deleted manually",
            "无法删除残留目录, 可以手动删除";
        LAUNCHING => "Starting Animeko", "正在启动 Animeko";
        LAUNCH_FAILED => "Failed to start Animeko", "启动 Animeko 失败";
        DONE => "Update finished", "更新完成";
        PRESS_ENTER => "Press Enter to close this window...", "按回车键关闭此窗口...";
        MISSING_VALUE => "Missing value for", "缺少参数值:";
        INVALID_VALUE => "Invalid value for", "参数值无效:";
        MISSING_ARGUMENT => "Missing argument", "缺少参数";
        UNKNOWN_ARGUMENT => "Unknown argument", "未知参数";
    }
}

#[cfg(windows)]
fn is_chinese() -> bool {
    use windows_sys::Win32::Globalization::GetUserDefaultUILanguage;
    const LANG_CHINESE: u16 = 0x04;
    // SAFETY: 无参数的系统调用.
    let language = unsafe { GetUserDefaultUILanguage() };
    language & 0x3FF == LANG_CHINESE
}

#[cfg(not(windows))]
fn is_chinese() -> bool {
    std::env::var("LANG")
        .map(|lang| lang.starts_with("zh"))
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_executables_under_the_installation_directory() {
        let dir = Path::new("D:\\Apps\\Ani");
        assert!(is_under(Path::new("D:\\Apps\\Ani\\Ani.exe"), dir));
        assert!(is_under(
            Path::new("d:\\apps\\ani\\runtime\\bin\\java.exe"),
            dir
        ));
        assert!(is_under(Path::new("\\\\?\\D:\\Apps\\Ani\\Ani.exe"), dir));
        assert!(!is_under(Path::new("D:\\Apps\\Ani2\\Ani.exe"), dir));
        assert!(!is_under(Path::new("D:\\Apps\\Ani"), dir));
        assert!(!is_under(Path::new("C:\\Windows\\System32\\cmd.exe"), dir));
    }

    #[test]
    fn parses_staging_arguments() {
        let args = Args::parse(
            [
                "--pid",
                "42",
                "--app-dir",
                "C:\\Ani",
                "--staging",
                "C:\\Ani.update",
                "--no-launch",
            ]
            .iter()
            .map(OsString::from),
        )
        .unwrap();
        assert_eq!(args.pid, Some(42));
        assert_eq!(args.app_dir, PathBuf::from("C:\\Ani"));
        assert!(
            matches!(args.source, Source::Staging(ref p) if p == &PathBuf::from("C:\\Ani.update"))
        );
        assert!(!args.launch);
    }

    #[test]
    fn rejects_missing_source() {
        assert!(Args::parse(["--app-dir", "C:\\Ani"].iter().map(OsString::from)).is_err());
    }

    #[test]
    fn strips_common_root_when_extracting() {
        let dir = std::env::temp_dir().join(format!("ani-update-test-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        let archive = dir.join("pkg.zip");
        {
            let mut zip = zip::ZipWriter::new(File::create(&archive).unwrap());
            let options = zip::write::SimpleFileOptions::default();
            zip.add_directory("Ani/", options).unwrap();
            zip.start_file("Ani/Ani.exe", options).unwrap();
            zip.write_all(b"exe").unwrap();
            zip.start_file("Ani/app/a.jar", options).unwrap();
            zip.write_all(b"jar").unwrap();
            zip.finish().unwrap();
        }
        let staging = dir.join("staging");
        extract_archive(&archive, &staging).unwrap();
        assert_eq!(fs::read(staging.join("Ani.exe")).unwrap(), b"exe");
        assert_eq!(fs::read(staging.join("app").join("a.jar")).unwrap(), b"jar");
        fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn moves_files_and_removes_stale_managed_files() {
        let dir = std::env::temp_dir().join(format!("ani-update-move-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        let app = dir.join("Ani");
        let staging = dir.join("staging");
        fs::create_dir_all(app.join("app")).unwrap();
        fs::create_dir_all(staging.join("app")).unwrap();
        fs::write(app.join("Ani.exe"), b"old").unwrap();
        fs::write(app.join("app").join("stale.jar"), b"stale").unwrap();
        fs::write(app.join("user-notes.txt"), b"keep").unwrap();
        fs::write(staging.join("Ani.exe"), b"new").unwrap();
        fs::write(staging.join("app").join("new.jar"), b"new").unwrap();
        let mut log = Log { file: None };
        move_files(&app, &staging, &mut log).unwrap();
        assert_eq!(fs::read(app.join("Ani.exe")).unwrap(), b"new");
        assert_eq!(fs::read(app.join("app").join("new.jar")).unwrap(), b"new");
        assert!(!app.join("app").join("stale.jar").exists());
        assert!(app.join("user-notes.txt").exists());
        assert!(!staging.exists());
        fs::remove_dir_all(&dir).unwrap();
    }
}
