/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools

import kotlin.reflect.KClass

actual object ServiceLoader {
    actual fun <T : Any> loadServices(kClass: KClass<T>): List<T> {
        return emptyList()
    }
}

//actual fun createPlatformServiceLoader(): ServiceLoader {
//    return RegistryServiceLoader().apply {
//        register<MediaSourceFactory> { MikanMediaSource.Factory() }
//        register<MediaSourceFactory> { MikanCNMediaSource.Factory() }
//        register<TorrentDownloaderFactory> { AnitorrentDownloaderFactory() }
//    }
//}
//
//open class RegistryServiceLoader : ServiceLoader {
//    class Registry<T : Any>(
//        val kClass: KClass<T>,
//        val factory: () -> T,
//    )
//
//    val registries = mutableListOf<Registry<*>>()
//
//    value class RegisterBuilder<T : Any> @PublishedApi internal constructor(
//        private val register: (() -> T) -> Unit,
//    ) {
//        fun factory(factory: () -> T) {
//            register(factory)
//        }
//    }
//
//    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // 必须提供 kClass
//    inline fun <T : Any> register(
//        kClass: KClass<T>,
//        builderAction: RegisterBuilder<@kotlin.internal.NoInfer T>.() -> Unit,
//    ) {
//        RegisterBuilder {
//            registries.add(Registry(kClass, it))
//        }.run(builderAction)
//    }
//
//    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER") // 必须提供泛型
//    inline fun <reified T : Any> register(
//        builderAction: RegisterBuilder<@kotlin.internal.NoInfer T>.() -> Unit,
//    ) = register(T::class, builderAction)
//
//    override fun <T : Any> loadServices(service: KClass<T>): Sequence<T> {
//        return registries.asSequence()
//            .filter { it.kClass == service }
//            .map {
//                @Suppress("UNCHECKED_CAST")
//                it.factory() as T
//            }
//    }
//}
//
//internal fun RegistryServiceLoader.registerCommonServices() {
//    register<ServiceLoader> {
//        RegistryServiceLoader()
//    }
//}
