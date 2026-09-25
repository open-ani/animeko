/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.foundation.focus

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/** A destination can have overlapping node instances while a lazy layout moves it between parents. */
internal class TvFocusTarget(private val boundary: TvFocusBoundaryState?) {
    val requester = FocusRequester()
    private val nodes = mutableStateMapOf<Any, Boolean>()
    val attached: Boolean get() = nodes.isNotEmpty()
    var attachmentGeneration by mutableIntStateOf(0)
        private set
    val ready: Boolean get() = nodes.values.any { it } && boundary?.isActive != false

    fun attach(node: Any) {
        nodes[node] = false
        attachmentGeneration++
    }
    fun place(node: Any, placed: Boolean) {
        if (node in nodes) nodes[node] = placed
    }
    fun detach(node: Any) { nodes.remove(node) }
}

internal fun Modifier.tvFocusTarget(target: TvFocusTarget): Modifier =
    focusRequester(target.requester).then(TvFocusTargetElement(target))

private data class TvFocusTargetElement(val target: TvFocusTarget) : ModifierNodeElement<TvFocusTargetNode>() {
    override fun create() = TvFocusTargetNode(target)
    override fun update(node: TvFocusTargetNode) = node.update(target)
    override fun InspectorInfo.inspectableProperties() { name = "tvFocusTarget" }
}

private class TvFocusTargetNode(private var target: TvFocusTarget) : Modifier.Node(), LayoutAwareModifierNode {
    private var placed = false
    override fun onAttach() {
        target.attach(this)
    }
    override fun onPlaced(coordinates: LayoutCoordinates) {
        placed = coordinates.size.width > 0 && coordinates.size.height > 0
        target.place(this, placed)
    }
    override fun onDetach() {
        target.detach(this)
        placed = false
    }
    fun update(value: TvFocusTarget) {
        if (target === value) return
        target.detach(this)
        target = value
        if (isAttached) {
            target.attach(this)
            target.place(this, placed)
        }
    }
}
