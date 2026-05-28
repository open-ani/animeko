@file:Suppress(
    "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING",
    "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS",
    "EXPECT_ACTUAL_INCOMPATIBILITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_MODALITY"
)

package me.him188.ani.utils.xml

actual abstract class Node {
    actual open fun attr(attributeKey: String): String = ""
    actual open fun attributesSize(): Int = 0
    actual open fun attr(attributeKey: String, attributeValue: String?): Node = this
    actual open fun hasAttr(attributeKey: String): Boolean = false
    actual open fun removeAttr(attributeKey: String): Node = this
    actual open fun clearAttributes(): Node = this
    actual open fun setBaseUri(baseUri: String) {}
    actual open fun absUrl(attributeKey: String): String = attr(attributeKey)
    actual open fun childNode(index: Int): Node = childNodes()[index]
    actual open fun childNodes(): List<Node> = emptyList()
    actual open fun childNodesCopy(): List<Node> = childNodes()
    actual abstract fun childNodeSize(): Int
    actual abstract fun empty(): Node?
    actual open fun parent(): Node? = null
    actual fun parentNode(): Node? = parent()
    actual open fun root(): Node = this
    actual open fun ownerDocument(): Document? = this as? Document
    actual open fun remove() {}
    actual open fun before(html: String): Node = this
    actual open fun before(node: Node): Node = this
    actual open fun after(html: String): Node = this
    actual open fun after(node: Node): Node = this
    actual open fun wrap(html: String): Node = this
    actual open fun unwrap(): Node? = null
    actual open fun siblingNodes(): List<Node> = emptyList()
    actual open fun nextSibling(): Node? = null
    actual open fun previousSibling(): Node? = null
    actual open fun siblingIndex(): Int = 0
    actual open fun firstChild(): Node? = null
    actual open fun lastChild(): Node? = null
    actual open fun <T : Appendable> html(appendable: T): T = appendable
    actual open fun hasSameValue(o: Any?): Boolean = this === o
}

actual abstract class Element : Node() {
    actual open fun tagName(): String = ""
    actual open fun getElementsByTag(tagName: String): Elements = EmptyElements
    actual open fun getElementById(id: String): Element? = null
    actual open fun getElementsByClass(className: String): Elements = EmptyElements
    actual open fun getElementsByAttribute(key: String): Elements = EmptyElements
    actual open fun getElementsByAttributeStarting(keyPrefix: String): Elements = EmptyElements
    actual open fun getElementsByAttributeValue(key: String, value: String): Elements = EmptyElements
    actual open fun getElementsByAttributeValueNot(key: String, value: String): Elements = EmptyElements
    actual open fun getElementsByAttributeValueStarting(key: String, valuePrefix: String): Elements = EmptyElements
    actual open fun getElementsByAttributeValueEnding(key: String, valueSuffix: String): Elements = EmptyElements
    actual open fun getElementsByAttributeValueContaining(key: String, match: String): Elements = EmptyElements
    actual open fun getElementsByAttributeValueMatching(key: String, regex: String): Elements = EmptyElements
    actual open fun getElementsByIndexLessThan(index: Int): Elements = EmptyElements
    actual open fun getElementsByIndexGreaterThan(index: Int): Elements = EmptyElements
    actual open fun getElementsByIndexEquals(index: Int): Elements = EmptyElements
    actual open fun getElementsContainingText(searchText: String): Elements = EmptyElements
    actual open fun getElementsContainingOwnText(searchText: String): Elements = EmptyElements
    actual open fun getAllElements(): Elements = EmptyElements
    actual open fun text(): String = ""
    actual fun select(cssQuery: String): Elements = EmptyElements
    actual fun select(evaluator: Evaluator): Elements = EmptyElements
    actual fun childrenSize(): Int = 0
    actual fun children(): Elements = EmptyElements
    override fun childNodeSize(): Int = 0
    override fun empty(): Node? = this
}

actual abstract class Document : Element()
actual abstract class Evaluator
actual abstract class Elements : List<Element> {
    actual fun attr(attributeKey: String): String = firstOrNull()?.attr(attributeKey) ?: ""
    actual fun text(): String = joinToString(" ") { it.text() }
}

private object EmptyElements : Elements(), List<Element> by emptyList()
