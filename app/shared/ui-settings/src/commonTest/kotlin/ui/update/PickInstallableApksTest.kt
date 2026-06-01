/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 自动更新的选包逻辑. 这里出错的表现是"自动更新后安装提示不兼容"
 * (`INSTALL_FAILED_NO_MATCHING_ABIS`): downloadUrlAlternatives 被 FileDownloader 当成同一文件的
 * 备选源 (第一个成功即停), 所以列表首项必须是本机装得上的包, 且列表里不能混入其它架构.
 *
 * 入参是设备的**完整** ABI 列表 (`Build.SUPPORTED_ABIS`, 按设备偏好排序) 而不是单个首选 ABI ——
 * 首选 ABI 不一定有对应的包, 见下面 x86 电视模拟器那条.
 */
class PickInstallableApksTest {
    // 与 release 实际产物同名 (CI 按目录字母序上传, GitHub 也按文件名排序返回, 故 arm64 在最前)
    private val releaseAssets = listOf(
        asset("ani-6.0.1-arm64-v8a.apk"),
        asset("ani-6.0.1-armeabi-v7a.apk"),
        asset("ani-6.0.1-universal.apk"),
        asset("ani-6.0.1-x86_64.apk"),
    )

    private fun asset(name: String) = GitHubAsset(name, "https://example.com/$name")

    private fun List<GitHubAsset>.names() = map { it.name }

    @Test
    fun `arm64 device gets its own package first`() {
        assertEquals(
            listOf("ani-6.0.1-arm64-v8a.apk", "ani-6.0.1-universal.apk"),
            releaseAssets.pickInstallableApks(listOf("arm64-v8a", "armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `32-bit device does not get the arm64 package`() {
        assertEquals(
            listOf("ani-6.0.1-armeabi-v7a.apk", "ani-6.0.1-universal.apk"),
            releaseAssets.pickInstallableApks(listOf("armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `x86_64 device does not get the arm64 package`() {
        assertEquals(
            listOf("ani-6.0.1-x86_64.apk", "ani-6.0.1-universal.apk"),
            releaseAssets.pickInstallableApks(listOf("x86_64", "x86", "armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `x86 tv emulator takes the v7 package via translation`() {
        // 这正是报障的那台 AOSP_TV_on_x86: abilist = x86,armeabi-v7a,armeabi (无 64 位).
        // 首选 ABI 是 x86, 本项目不出这个包; 它靠 native bridge 支持 armeabi-v7a, 该装 v7 包.
        // 修复前设备被当成 arm64 (Arch 里没有 x86, 落到 else 分支), 拿到 arm64 包 → NO_MATCHING_ABIS
        assertEquals(
            listOf("ani-6.0.1-armeabi-v7a.apk", "ani-6.0.1-universal.apk"),
            releaseAssets.pickInstallableApks(listOf("x86", "armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `32-bit x86 does not get the x86_64 package`() {
        // "x86" 是 "x86_64" 的子串: 按子串匹配会把 64 位包当成装得上的, 必须按 -<架构>. 整段匹配
        val noArm = listOf(asset("ani-6.0.1-universal.apk"), asset("ani-6.0.1-x86_64.apk"))
        assertEquals(
            listOf("ani-6.0.1-universal.apk"),
            noArm.pickInstallableApks(listOf("x86", "armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `falls back to universal when the release has no package for this device`() {
        // 6.0.1 的真实情况: release 上只有 universal 与 x86_64
        val partial = listOf(asset("ani-6.0.1-universal.apk"), asset("ani-6.0.1-x86_64.apk"))
        assertEquals(
            listOf("ani-6.0.1-universal.apk"),
            partial.pickInstallableApks(listOf("arm64-v8a", "armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `unknown abi list keeps the original list`() {
        // 非 Android 平台拿不到 ABI 列表: 不筛, 保持旧行为
        assertEquals(releaseAssets.names(), releaseAssets.pickInstallableApks(emptyList()).names())
    }

    @Test
    fun `universal only release stays installable`() {
        val universalOnly = listOf(asset("ani-6.0.1-universal.apk"))
        assertEquals(
            universalOnly.names(),
            universalOnly.pickInstallableApks(listOf("armeabi-v7a", "armeabi")).names(),
        )
    }

    @Test
    fun `unrecognized naming falls back to the original list`() {
        // 命名规则变了 (没有架构后缀也没有 universal): 宁可退回旧行为, 也不要交出空列表
        val renamed = listOf(asset("ani-6.0.1.apk"))
        assertEquals(renamed.names(), renamed.pickInstallableApks(listOf("arm64-v8a")).names())
    }
}
