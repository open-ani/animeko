package me.him188.ani.datasources.api.source.parameter

sealed interface MediaSourceParameter<T> {
    val name: String
    val description: String?  // todo: how to localize?
    val default: () -> T
    val visibleWhen: MediaSourceParameterVisibilityCondition?
        get() = null

    fun parseFromString(value: String): T
}

/**
 * Shows a parameter only while [parameterName] has one of [acceptedValues].
 * A `null` condition means the parameter is always visible.
 */
data class MediaSourceParameterVisibilityCondition(
    val parameterName: String,
    val acceptedValues: Set<String>,
) {
    init {
        require(parameterName.isNotEmpty()) { "parameterName must not be empty" }
        require(acceptedValues.isNotEmpty()) { "acceptedValues must not be empty" }
    }
}

fun MediaSourceParameter<*>.hasValue(vararg acceptedValues: String): MediaSourceParameterVisibilityCondition =
    MediaSourceParameterVisibilityCondition(name, acceptedValues.toSet())

private val TrueValidator: (String) -> Boolean = { true }
private val NoopSanitizer: (String) -> String = { it }

class StringParameter(
    override val name: String,
    override val description: String? = null,
    override val default: () -> String,
    val placeholder: String? = null,
    val isRequired: Boolean = false,
    /**
     * 验证用户输入是否合法
     */
    validate: (String) -> Boolean = TrueValidator,
    /**
     * 用户每输入一个字都会用整个编辑框的值调用这个函数, 可用于自动清除首尾空格等
     */
    val sanitize: (String) -> String = NoopSanitizer,
    override val visibleWhen: MediaSourceParameterVisibilityCondition? = null,
    /**
     * Whether editing UIs should treat this value as sensitive and conceal it from view.
     */
    val isSensitive: Boolean = false,
) : MediaSourceParameter<String> {
    val validate: (String) -> Boolean = {
        if (isRequired && it.isBlank()) {
            false
        } else {
            validate(it)
        }
    }

    init {
        require(name.isNotEmpty()) { "name must not be empty" }
    }

    override fun parseFromString(value: String): String {
        return sanitize(value)
    }
}

data class BooleanParameter(
    override val name: String,
    override val description: String? = null,
    override val default: () -> Boolean,
    override val visibleWhen: MediaSourceParameterVisibilityCondition? = null,
) : MediaSourceParameter<Boolean> {
    init {
        require(name.isNotEmpty()) { "name must not be empty" }
    }

    override fun parseFromString(value: String): Boolean {
        return value.toBoolean()
    }
}

data class SimpleEnumParameter(
    override val name: String,
    val oneOf: List<String>,
    override val description: String? = null,
    override val default: () -> String,
    override val visibleWhen: MediaSourceParameterVisibilityCondition? = null,
) : MediaSourceParameter<String> {
    init {
        require(name.isNotEmpty()) { "name must not be empty" }
        require(oneOf.isNotEmpty()) { "oneOf must not be empty" }
    }

    override fun parseFromString(value: String): String {
        return value
    }
}
