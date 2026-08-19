/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.video.enhancement.shader.provider

import android.content.Context

actual object VideoEnhancementShaderProvider {
    private val shaderTemplatePlaceholder = Regex("""\{\{[A-Z_]+\}\}""")
    private const val RES_ROOT = "composeResources/me.him188.ani.utils.video.enhancement.shader.provider/files/shaders"

    fun getShaderSource(context: Context, shaderName: String): String =
        context.assets.open("$RES_ROOT/$shaderName").bufferedReader().use { it.readText() }

    fun renderShaderTemplate(template: String, vararg replacements: Pair<String, String>): String {
        var result = template
        replacements.forEach { (name, value) ->
            val placeholder = "{{$name}}"
            require(placeholder in result) { "Shader template does not contain $placeholder" }
            result = result.replace(placeholder, value)
        }
        require(!shaderTemplatePlaceholder.containsMatchIn(result)) {
            "Shader template contains an unresolved placeholder"
        }
        return result
    }
}
