/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.details

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import me.him188.ani.app.data.models.subject.RelatedCharacterInfo
import me.him188.ani.app.data.models.subject.RelatedPersonInfo
import me.him188.ani.app.data.models.subject.RelatedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.ui.subject.collection.components.AiringLabelState
import me.him188.ani.datasources.api.topic.UnifiedCollectionType

/**
 * 条目详情页 UI 状态. 所有属性 null 都表示正在加载中.
 */
@Stable
class SubjectDetailsState(
    // headers 信息, 如果这些非 null, 就直接显示, 没有渐入动画
    subjectInfoState: State<SubjectInfo?>,
    selfCollectionTypeState: State<UnifiedCollectionType?>,
    personsState: State<List<RelatedPersonInfo>?>,

    // 附加信息
    charactersState: State<List<RelatedCharacterInfo>?>,
    relatedSubjectsState: State<List<RelatedSubjectInfo>?>,
    val airingLabelState: AiringLabelState,
) {
    val info by derivedStateOf { subjectInfoState.value ?: me.him188.ani.app.data.models.SubjectInfo.Empty }

    val coverImageUrl by derivedStateOf { info.imageLarge }

    private val selfCollectionTypeOrNull by selfCollectionTypeState
    val selfCollectionType by derivedStateOf { selfCollectionTypeOrNull ?: UnifiedCollectionType.WISH }

    val selfCollected by derivedStateOf { this.selfCollectionType != UnifiedCollectionType.NOT_COLLECTED }

    private val charactersOrNull by charactersState
    val characters by derivedStateOf { charactersOrNull ?: emptyList() }

    private val personsOrNull by personsState
    val persons by derivedStateOf { personsOrNull ?: emptyList() }

    private val relatedSubjectsOrNull by relatedSubjectsState
    val relatedSubjects by derivedStateOf { relatedSubjectsOrNull ?: emptyList() }

    /**
     * 有任何一个数据为空
     */
    val isLoading: Boolean by derivedStateOf {
        selfCollectionTypeOrNull == null
                || charactersOrNull == null || info === me.him188.ani.app.data.models.SubjectInfo.Empty
                || personsOrNull == null
                || relatedSubjectsOrNull == null
                || airingLabelState.isLoading
    }
}
