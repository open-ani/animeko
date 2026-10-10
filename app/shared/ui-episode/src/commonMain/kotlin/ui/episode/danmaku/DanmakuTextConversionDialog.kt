/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.episode.danmaku

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.danmaku.DanmakuTextConversionPreview
import me.him188.ani.app.domain.danmaku.DanmakuTextConversionSample
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_danmaku_confirm
import me.him188.ani.app.ui.lang.subject_episode_danmaku_text_conversion_desc
import me.him188.ani.app.ui.lang.subject_episode_danmaku_text_conversion_follow_global
import me.him188.ani.app.ui.lang.subject_episode_danmaku_text_conversion_preview_hint
import me.him188.ani.app.ui.lang.subject_episode_danmaku_text_conversion_reset_overrides
import me.him188.ani.app.ui.lang.subject_episode_danmaku_text_conversion_section_all
import me.him188.ani.app.ui.lang.subject_episode_danmaku_text_conversion_section_per_source
import me.him188.ani.app.ui.lang.subject_episode_video_settings_text_conversion
import me.him188.ani.danmaku.api.DanmakuServiceId
import me.him188.ani.danmaku.ui.DanmakuTextConversion
import org.jetbrains.compose.resources.stringResource

/**
 * 弹幕文字转换的统一设置对话框: 全局目标与按来源覆盖在同一处展示和修改.
 *
 * 全局与按来源可能互相冲突 (按来源覆盖优先), 如果只在一个地方看不到完整状态会令用户困惑,
 * 因此播放器弹幕设置与每个弹幕源菜单的"简繁转换"入口都打开这一个对话框.
 * 所有选择立即生效.
 *
 * 按来源列表只列出本次实际拉取到的 [sources] (每个来源设置页也只列这些), 不写死来源清单,
 * 因为能拉到的来源取决于番剧、匹配结果与网络, 列出一个不存在的来源没有意义.
 * 另外补上 [overrides] 里残留的来源, 否则那些设置将无法在这里清除.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DanmakuTextConversionSettingsDialog(
    sources: List<DanmakuServiceId>,
    global: DanmakuTextConversion,
    overrides: Map<DanmakuServiceId, DanmakuTextConversion>,
    onSetGlobal: (DanmakuTextConversion) -> Unit,
    onSetOverride: (DanmakuServiceId, DanmakuTextConversion?) -> Unit,
    onResetOverrides: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val confirmText = stringResource(Lang.settings_danmaku_confirm)
    val followGlobalText = stringResource(Lang.subject_episode_danmaku_text_conversion_follow_global)
    // 转换结果与来源无关, 示例只按目标文字算一次, 供所有来源行共用
    val previews by produceState<Map<DanmakuTextConversion, DanmakuTextConversionSample>>(
        initialValue = emptyMap(),
    ) {
        value = DanmakuTextConversion.entries.associateWith { target ->
            DanmakuTextConversionPreview.sample(target)
        }
    }

    AlertDialog(
        modifier = modifier.testTag("danmaku-text-conversion-dialog"),
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = onDismissRequest,
                modifier = Modifier.testTag("danmaku-text-conversion-close"),
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            if (overrides.isNotEmpty()) {
                TextButton(
                    onClick = onResetOverrides,
                    modifier = Modifier.testTag("danmaku-text-conversion-reset"),
                ) {
                    Text(stringResource(Lang.subject_episode_danmaku_text_conversion_reset_overrides))
                }
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Lang.subject_episode_video_settings_text_conversion))
                Text(
                    stringResource(Lang.subject_episode_danmaku_text_conversion_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConversionSectionTitle(
                    stringResource(Lang.subject_episode_danmaku_text_conversion_section_all),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (option in DanmakuTextConversion.entries) {
                        FilterChip(
                            selected = global == option,
                            onClick = { onSetGlobal(option) },
                            leadingIcon = {
                                if (global == option) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                            label = { Text(danmakuTextConversionText(option), maxLines = 1) },
                            modifier = Modifier.testTag("danmaku-text-conversion-global-${option.name}"),
                        )
                    }
                }
                GlobalConversionPreview(global, previews)

                val serviceIds = (sources + overrides.keys).distinct()
                if (serviceIds.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    ConversionSectionTitle(
                        stringResource(Lang.subject_episode_danmaku_text_conversion_section_per_source),
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Column {
                            serviceIds.forEachIndexed { index, serviceId ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        Modifier.padding(start = 48.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )
                                }
                                PerSourceConversionRow(
                                    serviceId = serviceId,
                                    override = overrides[serviceId],
                                    global = global,
                                    followGlobalText = followGlobalText,
                                    previews = previews,
                                    onSelect = { onSetOverride(serviceId, it) },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ConversionSectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 全局选择下方的一行示例: 说明当前选择会把弹幕变成什么样.
 */
@Composable
private fun GlobalConversionPreview(
    global: DanmakuTextConversion,
    previews: Map<DanmakuTextConversion, DanmakuTextConversionSample>,
) {
    // 原样不转换, 没有可展示的变化, 省掉这一行让默认状态更干净
    if (global == DanmakuTextConversion.ORIGINAL) return
    val preview = previews[global] ?: return
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(Lang.subject_episode_danmaku_text_conversion_preview_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preview.isChanged) {
            Text(
                preview.sourceText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "→",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            preview.convertedText,
            style = MaterialTheme.typography.labelMedium,
            color = if (preview.isChanged) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (preview.isChanged) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun PerSourceConversionRow(
    serviceId: DanmakuServiceId,
    override: DanmakuTextConversion?,
    global: DanmakuTextConversion,
    followGlobalText: String,
    previews: Map<DanmakuTextConversion, DanmakuTextConversionSample>,
    onSelect: (DanmakuTextConversion?) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("danmaku-text-conversion-source-${serviceId.value}")
                .clickable { expanded = true }
                .heightIn(min = 52.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DanmakuServiceIcon(serviceId, size = 24)
            Spacer(Modifier.width(12.dp))
            Text(
                renderDanmakuServiceId(serviceId),
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = override?.let { danmakuTextConversionText(it) } ?: followGlobalText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (override != null) FontWeight.Medium else FontWeight.Normal,
                color = if (override != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
            )
            if (override == null && global != DanmakuTextConversion.ORIGINAL) {
                // "跟随全局" 具体跟随到什么, 直接写出来, 免得用户回上面找
                Text(
                    " · ${danmakuTextConversionText(global)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 220.dp)
                .testTag("danmaku-text-conversion-menu-${serviceId.value}"),
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            if (global == DanmakuTextConversion.ORIGINAL) {
                                followGlobalText
                            } else {
                                "$followGlobalText · ${danmakuTextConversionText(global)}"
                            },
                        )
                        previews[global]?.let { sample ->
                            Text(
                                sample.convertedText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (override == null) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                },
                modifier = Modifier.testTag(
                    "danmaku-text-conversion-option-${serviceId.value}-follow_global",
                ),
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            for (option in DanmakuTextConversion.entries) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(danmakuTextConversionText(option))
                            previews[option]?.let { sample ->
                                Text(
                                    sample.convertedText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    trailingIcon = {
                        if (override == option) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    modifier = Modifier.testTag(
                        "danmaku-text-conversion-option-${serviceId.value}-${option.name}",
                    ),
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
