/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress(
    "ACTUAL_CLASSIFIER_MUST_HAVE_THE_SAME_MEMBERS_AS_NON_FINAL_EXPECT_CLASSIFIER_WARNING",
    "NO_ACTUAL_CLASS_MEMBER_FOR_EXPECTED_CLASS", "ACTUAL_WITHOUT_EXPECT", "EXPECT_ACTUAL_INCOMPATIBILITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_MODALITY",
    "EXPECT_ACTUAL_INCOMPATIBLE_CLASS_SCOPE",

    "EXPECT_ACTUAL_IR_INCOMPATIBILITY",
    "EXPECT_ACTUAL_IR_MISMATCH",
)

package me.him188.ani.utils.xml

/*
 * 主要参考 JVM 的 Jsoup, 缺什么就加什么. actual 是 magic typealias.
 */

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Document : Element

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Element : Node {
    fun tagName(): String

    /**
     * Finds elements, including and recursively under this element, with the specified tag name.
     * @param tagName The tag name to search for (case insensitively).
     * @return a matching unmodifiable list of elements. Will be empty if this element and none of its children match.
     */
    open fun getElementsByTag(tagName: String): Elements

    /**
     * Find an element by ID, including or under this element.
     *
     *
     * Note that this finds the first matching ID, starting with this element. If you search down from a different
     * starting point, it is possible to find a different element by ID. For unique element by ID within a Document,
     * use [Document.getElementById]
     * @param id The ID to search for.
     * @return The first matching element by ID, starting with this element, or null if none found.
     */
//    @Nullable
    open fun getElementById(id: String): Element?

    /**
     * Find elements that have this class, including or under this element. Case-insensitive.
     *
     *
     * Elements can have multiple classes (e.g. `<div class="header round first">`). This method
     * checks each class, so you can find the above with `el.getElementsByClass("header");`.
     *
     * @param className the name of the class to search for.
     * @return elements with the supplied class name, empty if none
     * @see .hasClass
     * @see .classNames
     */
    open fun getElementsByClass(className: String): Elements

    /**
     * Find elements that have a named attribute set. Case-insensitive.
     *
     * @param key name of the attribute, e.g. `href`
     * @return elements that have this attribute, empty if none
     */
    open fun getElementsByAttribute(key: String): Elements

    /**
     * Find elements that have an attribute name starting with the supplied prefix. Use `data-` to find elements
     * that have HTML5 datasets.
     * @param keyPrefix name prefix of the attribute e.g. `data-`
     * @return elements that have attribute names that start with the prefix, empty if none.
     */
    open fun getElementsByAttributeStarting(keyPrefix: String): Elements

    /**
     * Find elements that have an attribute with the specific value. Case-insensitive.
     *
     * @param key name of the attribute
     * @param value value of the attribute
     * @return elements that have this attribute with this value, empty if none
     */
    open fun getElementsByAttributeValue(
        key: String,
        value: String,
    ): Elements

    /**
     * Find elements that either do not have this attribute, or have it with a different value. Case-insensitive.
     *
     * @param key name of the attribute
     * @param value value of the attribute
     * @return elements that do not have a matching attribute
     */
    open fun getElementsByAttributeValueNot(
        key: String,
        value: String,
    ): Elements

    /**
     * Find elements that have attributes that start with the value prefix. Case-insensitive.
     *
     * @param key name of the attribute
     * @param valuePrefix start of attribute value
     * @return elements that have attributes that start with the value prefix
     */
    open fun getElementsByAttributeValueStarting(
        key: String,
        valuePrefix: String,
    ): Elements

    /**
     * Find elements that have attributes that end with the value suffix. Case-insensitive.
     *
     * @param key name of the attribute
     * @param valueSuffix end of the attribute value
     * @return elements that have attributes that end with the value suffix
     */
    open fun getElementsByAttributeValueEnding(
        key: String,
        valueSuffix: String,
    ): Elements

    /**
     * Find elements that have attributes whose value contains the match string. Case-insensitive.
     *
     * @param key name of the attribute
     * @param match substring of value to search for
     * @return elements that have attributes containing this text
     */
    open fun getElementsByAttributeValueContaining(
        key: String,
        match: String,
    ): Elements

    /**
     * Find elements that have attributes whose values match the supplied regular expression.
     * @param key name of the attribute
     * @param regex regular expression to match against attribute values. You can use [embedded flags](http://java.sun.com/docs/books/tutorial/essential/regex/pattern.html#embedded) (such as (?i) and (?m) to control regex options.
     * @return elements that have attributes matching this regular expression
     */
    open fun getElementsByAttributeValueMatching(
        key: String,
        regex: String,
    ): Elements

    /**
     * Find elements whose sibling index is less than the supplied index.
     * @param index 0-based index
     * @return elements less than index
     */
    open fun getElementsByIndexLessThan(index: Int): Elements

    /**
     * Find elements whose sibling index is greater than the supplied index.
     * @param index 0-based index
     * @return elements greater than index
     */
    open fun getElementsByIndexGreaterThan(index: Int): Elements

    /**
     * Find elements whose sibling index is equal to the supplied index.
     * @param index 0-based index
     * @return elements equal to index
     */
    open fun getElementsByIndexEquals(index: Int): Elements

    /**
     * Find elements that contain the specified string. The search is case-insensitive. The text may appear directly
     * in the element, or in any of its descendants.
     * @param searchText to look for in the element's text
     * @return elements that contain the string, case-insensitive.
     * @see Element.text
     */
    open fun getElementsContainingText(searchText: String): Elements

    /**
     * Find elements that directly contain the specified string. The search is case-insensitive. The text must appear directly
     * in the element, not in any of its descendants.
     * @param searchText to look for in the element's own text
     * @return elements that contain the string, case-insensitive.
     * @see Element.ownText
     */
    open fun getElementsContainingOwnText(searchText: String): Elements

    open fun getAllElements(): Elements

    /**
     * Gets the **normalized, combined text** of this element and all its children. Whitespace is normalized and
     * trimmed.
     *
     * For example, given HTML `<p>Hello  <b>there</b> now! </p>`, `p.text()` returns `"Hello there
     * now!"`
     *
     * If you do not want normalized text, use [.wholeText]. If you want just the text of this node (and not
     * children), use [.ownText]
     *
     * Note that this method returns the textual content that would be presented to a reader. The contents of data
     * nodes (such as `<script>` tags) are not considered text. Use [.data] or [.html] to retrieve
     * that content.
     *
     * @return decoded, normalized text, or empty string if none.
     * @see .wholeText
     * @see .ownText
     * @see .textNodes
     */
    open fun text(): String

    @Deprecated("This will throw if the cssQuery in invalid. Use the overload that uses a evaluator instead.")
    fun select(cssQuery: String): Elements
    fun select(evaluator: Evaluator): Elements

    fun childrenSize(): Int
    fun children(): Elements
}

/**
 * @see QueryParser.parseSelector
 * @see QueryParser.parseSelectorOrNull
 */
@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Evaluator

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Elements : List<Element> {
    fun attr(attributeKey: String): String
    fun text(): String
}

@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")
expect abstract class Node {
    /**
     * Get an attribute's value by its key. **Case insensitive**
     *
     *
     * To get an absolute URL from an attribute that may be a relative URL, prefix the key with `**abs:**`,
     * which is a shortcut to the [.absUrl] method.
     *
     * E.g.:
     * <blockquote>`String url = a.attr("abs:href");`</blockquote>
     *
     * @param attributeKey The attribute key.
     * @return The attribute, or empty string if not present (to avoid nulls).
     * @see .attributes
     * @see .hasAttr
     * @see .absUrl
     */
    open fun attr(attributeKey: String): String

    /**
     * Get the number of attributes that this Node has.
     * @return the number of attributes
     */
    open fun attributesSize(): Int

    /**
     * Set an attribute (key=value). If the attribute already exists, it is replaced. The attribute key comparison is
     * **case insensitive**. The key will be set with case sensitivity as set in the parser settings.
     * @param attributeKey The attribute key.
     * @param attributeValue The attribute value.
     * @return this (for chaining)
     */
    @Suppress("EXPECT_ACTUAL_MISMATCH") // nullability //             IrActualizationErrors.EXPECT_ACTUAL_MISMATCH,
    open fun attr(
        attributeKey: String,
        attributeValue: String?,
    ): Node

    /**
     * Test if this Node has an attribute. **Case insensitive**.
     * @param attributeKey The attribute key to check.
     * @return true if the attribute exists, false if not.
     */
    open fun hasAttr(attributeKey: String): Boolean

    /**
     * Remove an attribute from this node.
     * @param attributeKey The attribute to remove.
     * @return this (for chaining)
     */
    open fun removeAttr(attributeKey: String): Node

    /**
     * Clear (remove) each of the attributes in this node.
     * @return this, for chaining
     */
    open fun clearAttributes(): Node

    /**
     * Update the base URI of this node and all of its descendants.
     * @param baseUri base URI to set
     */
    open fun setBaseUri(baseUri: String)

    /**
     * Get an absolute URL from a URL attribute that may be relative (such as an `<a href>` or
     * `<img src>`).
     *
     *
     * E.g.: `String absUrl = linkEl.absUrl("href");`
     *
     *
     *
     * If the attribute value is already absolute (i.e. it starts with a protocol, like
     * `http://` or `https://` etc), and it successfully parses as a URL, the attribute is
     * returned directly. Otherwise, it is treated as a URL relative to the element's [.baseUri], and made
     * absolute using that.
     *
     *
     *
     * As an alternate, you can use the [.attr] method with the `abs:` prefix, e.g.:
     * `String absUrl = linkEl.attr("abs:href");`
     *
     *
     * @param attributeKey The attribute key
     * @return An absolute URL if one could be made, or an empty string (not null) if the attribute was missing or
     * could not be made successfully into a URL.
     * @see .attr
     *
     * @see java.net.URL.URL
     */
    open fun absUrl(attributeKey: String): String

    /**
     * Get a child node by its 0-based index.
     * @param index index of child node
     * @return the child node at this index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    open fun childNode(index: Int): Node

    /**
     * Get this node's children. Presented as an unmodifiable list: new children can not be added, but the child nodes
     * themselves can be manipulated.
     * @return list of children. If no children, returns an empty list.
     */
    open fun childNodes(): List<Node>

    /**
     * Returns a deep copy of this node's children. Changes made to these nodes will not be reflected in the original
     * nodes
     * @return a deep copy of this node's children
     */
    open fun childNodesCopy(): List<Node>

    /**
     * Get the number of child nodes that this node holds.
     * @return the number of child nodes that this node holds.
     */
    abstract fun childNodeSize(): Int

    /**
     * Delete all this node's children.
     * @return this node, for chaining
     */
    abstract fun empty(): Node?

    /**
     * Gets this node's parent node.
     * @return parent node; or null if no parent.
     * @see .hasParent
     */
    open fun parent(): Node?

    /**
     * Gets this node's parent node. Not overridable by extending classes, so useful if you really just need the Node type.
     * @return parent node; or null if no parent.
     */
    fun parentNode(): Node?

    /**
     * Get this node's root node; that is, its topmost ancestor. If this node is the top ancestor, returns `this`.
     * @return topmost ancestor.
     */
    open fun root(): Node

    /**
     * Gets the Document associated with this Node.
     * @return the Document associated with this Node, or null if there is no such Document.
     */
    open fun ownerDocument(): Document?

    /**
     * Remove (delete) this node from the DOM tree. If this node has children, they are also removed. If this node is
     * an orphan, nothing happens.
     */
    open fun remove()

    /**
     * Insert the specified HTML into the DOM before this node (as a preceding sibling).
     * @param html HTML to add before this node
     * @return this node, for chaining
     * @see .after
     */
    open fun before(html: String): Node

    /**
     * Insert the specified node into the DOM before this node (as a preceding sibling).
     * @param node to add before this node
     * @return this node, for chaining
     * @see .after
     */
    open fun before(node: Node): Node

    /**
     * Insert the specified HTML into the DOM after this node (as a following sibling).
     * @param html HTML to add after this node
     * @return this node, for chaining
     * @see .before
     */
    open fun after(html: String): Node

    /**
     * Insert the specified node into the DOM after this node (as a following sibling).
     * @param node to add after this node
     * @return this node, for chaining
     * @see .before
     */
    open fun after(node: Node): Node

    /**
     * Wrap the supplied HTML around this node.
     *
     * @param html HTML to wrap around this node, e.g. `<div class="head"></div>`. Can be arbitrarily deep. If
     * the input HTML does not parse to a result starting with an Element, this will be a no-op.
     * @return this node, for chaining.
     */
    open fun wrap(html: String): Node

    /**
     * Removes this node from the DOM, and moves its children up into the node's parent. This has the effect of dropping
     * the node but keeping its children.
     *
     *
     * For example, with the input html:
     *
     *
     * `<div>One <span>Two <b>Three</b></span></div>`
     * Calling `element.unwrap()` on the `span` element will result in the html:
     *
     * `<div>One Two <b>Three</b></div>`
     * and the `"Two "` [TextNode] being returned.
     *
     * @return the first child of this node, after the node has been unwrapped. @{code Null} if the node had no children.
     * @see .remove
     * @see .wrap
     */
    open fun unwrap(): Node?

    /**
     * Retrieves this node's sibling nodes. Similar to [node.parent.childNodes()][.childNodes], but does not
     * include this node (a node is not a sibling of itself).
     * @return node siblings. If the node has no parent, returns an empty list.
     */
    open fun siblingNodes(): List<Node>

    /**
     * Get this node's next sibling.
     * @return next sibling, or {@code null} if this is the last sibling
     */
    open fun nextSibling(): Node?

    /**
     * Get this node's previous sibling.
     * @return the previous sibling, or @{code null} if this is the first sibling
     */
    open fun previousSibling(): Node?

    /**
     * Get the list index of this node in its node sibling list. E.g. if this is the first node
     * sibling, returns 0.
     * @return position in node sibling list
     * @see com.fleeksoft.ksoup.nodes.Element.elementSiblingIndex
     */
    open fun siblingIndex(): Int

    /**
     * Gets the first child node of this node, or `null` if there is none. This could be any Node type, such as an
     * Element, TextNode, Comment, etc. Use [Element.firstElementChild] to get the first Element child.
     * @return the first child node, or null if there are no children.
     * @see Element.firstElementChild
     * @see .lastChild
     */
    open fun firstChild(): Node?

    /**
     * Gets the last child node of this node, or `null` if there is none.
     * @return the last child node, or null if there are no children.
     * @see Element.lastElementChild
     * @see .firstChild
     */
    open fun lastChild(): Node?

    /**
     * Write this node and its children to the given [Appendable].
     *
     * @param appendable the [Appendable] to write to.
     * @return the supplied [Appendable], for chaining.
     */
    open fun <T : Appendable> html(appendable: T): T

    /**
     * Check if this node has the same content as another node. A node is considered the same if its name, attributes and content match the
     * other node; particularly its position in the tree does not influence its similarity.
     * @param o other object to compare to
     * @return true if the content of this node is the same as the other
     */
    open fun hasSameValue(o: Any?): Boolean
}

