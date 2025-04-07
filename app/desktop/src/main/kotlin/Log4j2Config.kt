/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration
import java.io.File

object Log4j2Config {
    fun configureLogging(
        logsFolder: File,
    ) {
        val builder: ConfigurationBuilder<BuiltConfiguration> = ConfigurationBuilderFactory.newConfigurationBuilder()

        // Console appender
        val charset = "UTF-8"
        val consoleLayoutBuilder: LayoutComponentBuilder = builder.newLayout("PatternLayout")
            .addAttribute(
                "pattern",
                "%highlight{%d [%-5level] %c{1}: %msg%n%throwable}{FATAL=red bold, ERROR=red, WARN=yellow, INFO=transparent, DEBUG=bright_blue, TRACE=bright_green}",
            )
            .addAttribute("charset", charset) // Specify charset
        val consoleAppenderBuilder: AppenderComponentBuilder = builder.newAppender("STDOUT", "Console")
            .add(consoleLayoutBuilder)
        builder.add(consoleAppenderBuilder)

        // File appender
        val fileLayoutBuilder: LayoutComponentBuilder = builder.newLayout("PatternLayout")
            .addAttribute("pattern", "%d [%-5level] %c: %msg%n%throwable")
            .addAttribute("charset", charset) // Specify charset
        val fileAppenderBuilder: AppenderComponentBuilder = builder.newAppender("FILE", "RollingFile")
            .addAttribute("fileName", "${logsFolder.absolutePath}/app.log")
            .addAttribute("filePattern", "${logsFolder.absolutePath}/app-%d{yyyy-MM-dd}.log")
            .add(fileLayoutBuilder)

        fileAppenderBuilder.addComponent(
            builder.newComponent("Policies")
                .addComponent(
                    builder.newComponent("TimeBasedTriggeringPolicy")
                        .addAttribute("interval", "1")
                        .addAttribute("modulate", true),
                ),
        )
        fileAppenderBuilder.addComponent(
            builder.newComponent("DefaultRolloverStrategy")
                .addAttribute("max", "7"),
        )
        builder.add(fileAppenderBuilder)

        // Root logger
        builder.add(
            builder.newRootLogger(Level.ALL)
                .add(builder.newAppenderRef("STDOUT").addAttribute("level", Level.DEBUG))
                .add(builder.newAppenderRef("FILE")),
        )
        builder.add(
            builder.newLogger("io.ktor.client.plugins", Level.DEBUG)
                .addAttribute("additivity", false),
        )
        builder.add(
            builder.newLogger("org.apache.hc.client5.http.wire", Level.OFF)
                .addAttribute("additivity", false),
        )
        builder.add(
            builder.newLogger("org.openqa.selenium", Level.OFF)
                .addAttribute("additivity", false),
        )

        val ctx: LoggerContext = LogManager.getContext(false) as LoggerContext
        val config: Configuration = builder.build()
        ctx.start(config)
//        Configurator.initialize(builder.build())
    }
}