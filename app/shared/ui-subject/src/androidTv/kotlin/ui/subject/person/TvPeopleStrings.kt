/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.leanback.ui.subject.person

import androidx.compose.runtime.Composable
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.people_character_intro
import me.him188.ani.app.ui.lang.people_voice_actor_intro
import me.him188.ani.app.ui.lang.people_staff_intro
import me.him188.ani.app.ui.lang.people_career_singer
import me.him188.ani.app.ui.lang.people_career_director
import me.him188.ani.app.ui.lang.person_details_career_actor
import me.him188.ani.app.ui.lang.person_details_career_artist
import me.him188.ani.app.ui.lang.person_details_career_illustrator
import me.him188.ani.app.ui.lang.person_details_career_mangaka
import me.him188.ani.app.ui.lang.person_details_career_producer
import me.him188.ani.app.ui.lang.person_details_career_seiyu
import me.him188.ani.app.ui.lang.person_details_career_writer
import me.him188.ani.app.ui.lang.person_details_role_character
import me.him188.ani.app.ui.lang.person_details_role_mecha
import me.him188.ani.app.ui.lang.person_details_role_organization
import me.him188.ani.app.ui.lang.person_details_role_ship
import me.him188.ani.app.ui.lang.subject_details_staff
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun peopleKindLabel(kind: TvPeopleKind) = stringResource(when (kind) {
    TvPeopleKind.Character -> Lang.person_details_role_character
    TvPeopleKind.VoiceActor -> Lang.person_details_career_seiyu
    TvPeopleKind.Staff -> Lang.subject_details_staff
})

@Composable
internal fun peopleIntroductionTitle(kind: TvPeopleKind) = stringResource(when (kind) {
    TvPeopleKind.Character -> Lang.people_character_intro
    TvPeopleKind.VoiceActor -> Lang.people_voice_actor_intro
    TvPeopleKind.Staff -> Lang.people_staff_intro
})

@Composable
internal fun peopleMetadata(kind: TvPeopleKind, profile: TvPeopleProfile): String {
    if (kind == TvPeopleKind.Character) return stringResource(when (profile.role) {
        2 -> Lang.person_details_role_mecha
        3 -> Lang.person_details_role_ship
        4 -> Lang.person_details_role_organization
        else -> Lang.person_details_role_character
    })
    return profile.careers.distinct().map { career ->
        val resource = when (career) {
            "seiyu" -> Lang.person_details_career_seiyu
            "producer" -> Lang.person_details_career_producer
            "mangaka" -> Lang.person_details_career_mangaka
            "artist" -> Lang.person_details_career_artist
            "writer" -> Lang.person_details_career_writer
            "illustrator" -> Lang.person_details_career_illustrator
            "actor" -> Lang.person_details_career_actor
            "singer" -> Lang.people_career_singer
            "director" -> Lang.people_career_director
            else -> null
        }
        resource?.let { stringResource(it) } ?: career
    }.filter { it.isNotBlank() }.joinToString(" · ").ifBlank { peopleKindLabel(kind) }
}
