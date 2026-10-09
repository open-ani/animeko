/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.watchtogether

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchTogetherPlayerControllerTest {
    @Test
    fun `bubble is visible while nobody requests hiding`() {
        val controller = WatchTogetherPlayerController()

        assertTrue(controller.isDraggablePopupVisible)
    }

    @Test
    fun `bubble is hidden while any requester requests hiding`() {
        val controller = WatchTogetherPlayerController()

        controller.setRequestHidden("player page", true)

        assertFalse(controller.isDraggablePopupVisible)
    }

    @Test
    fun `repeating the request of the same requester is idempotent`() {
        val controller = WatchTogetherPlayerController()

        controller.setRequestHidden("player page", true)
        controller.setRequestHidden("player page", true)

        assertEquals(listOf<Any>("player page"), controller.getHideRequesters())
    }

    /**
     * 一起看跟播切集会重建播放页: 旧页面撤销自己的请求不能让新页面刚提出的请求失效.
     */
    @Test
    fun `bubble stays hidden when one requester cancels while another still requests`() {
        val controller = WatchTogetherPlayerController()
        controller.setRequestHidden("previous page", true)
        controller.setRequestHidden("current page", true)

        controller.setRequestHidden("previous page", false)

        assertEquals(listOf<Any>("current page"), controller.getHideRequesters())
        assertFalse(controller.isDraggablePopupVisible)
    }

    @Test
    fun `bubble is visible again after the last requester cancels`() {
        val controller = WatchTogetherPlayerController()

        controller.setRequestHidden("player page", true)
        controller.setRequestHidden("player page", false)

        assertTrue(controller.isDraggablePopupVisible)
    }
}
