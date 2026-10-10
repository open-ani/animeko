/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.install

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlin.uuid.Uuid

/**
 * 本机安装的随机 ID ([InstallInfo.installId]). 随请求发给 Ani 服务器, 让服务器区分同一用户的不同设备
 * (同型号设备的 User-Agent 相同).
 *
 * ID 在第一次 [load] 时生成, 之后不变, 直到应用数据被清除.
 */
class InstallIdRepository(
    private val store: DataStore<InstallInfo>,
) {
    private val loaded = MutableStateFlow<String?>(null)

    /**
     * 已经加载到内存的 ID, [load] 完成前为 `null`. 不读磁盘, 可以在每次请求时调用.
     */
    val currentId: String? get() = loaded.value

    /**
     * 读取 ID, 不存在时生成并保存. 应在启动早期、发出 Ani 请求之前调用.
     */
    suspend fun load(): String {
        val id = store.data.first().installId
            ?: store.updateData { info ->
                if (info.installId != null) info else info.copy(installId = Uuid.random().toString())
            }.installId
        checkNotNull(id)
        loaded.value = id
        return id
    }
}
