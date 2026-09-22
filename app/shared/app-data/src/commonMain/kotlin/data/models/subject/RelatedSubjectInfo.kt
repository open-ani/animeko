package me.him188.ani.app.data.models.subject

import androidx.compose.runtime.Immutable

@Immutable
class RelatedSubjectInfo(
    val subjectId: Int,
    /**
     * null 表示其他类型
     */
    val relation: SubjectRelation?,
    val name: String?,
    val nameCn: String,
    val image: String?,
) {
    val displayName get() = nameCn.ifBlank { name } ?: nameCn

    companion object {
        fun sortList(subjectList: List<RelatedSubjectInfo>): List<RelatedSubjectInfo> {
            return subjectList.sortedByDescending {
                when (it.relation) {
                    SubjectRelation.PREQUEL -> 10
                    SubjectRelation.SEQUEL -> 9
                    SubjectRelation.DERIVED -> 8
                    SubjectRelation.SPECIAL -> 7
                    else -> 0
                }
            }
        }
    }
}

enum class SubjectRelation {
    /**
     * 对应 Bangumi "续集", 包括第二季, 外传
     */
    SEQUEL,

    /**
     * 对应 Bangumi "前传"
     */
    PREQUEL,

    /**
     * 对应 Bangumi "衍生", 例如《转生史莱姆日记》
     */
    DERIVED,

    /**
     * 对应 Bangumi "番外篇". 例如 OAD
     */
    SPECIAL,

    /**
     * 对应 Bangumi "主线故事", 即番外篇和衍生作品所依附的原作
     */
    MAIN_STORY,

    /**
     * 对应 Bangumi "总集篇", 包括剧场版形式的总集篇
     */
    COMPILATION,
}
