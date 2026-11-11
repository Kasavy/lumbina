package dev.lumbina.tv.platform.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The walk under the touchpad's pointer, on a tree of its own. */
class UnderPointerTest {

    private class Node(
        val name: String,
        val hit: Boolean = true,
        val clickable: Boolean = false,
        val along: Boolean = false,
        val axisless: Boolean = false,
        val children: List<Node> = emptyList(),
    )

    private class Tree : PointerTree<Node> {
        val released = mutableListOf<String>()

        override fun hits(node: Node) = node.hit

        override fun childCount(node: Node) = node.children.size

        override fun child(node: Node, index: Int) = node.children[index]

        override fun release(node: Node) {
            released += node.name
        }
    }

    private val tree = Tree()

    @Test
    fun `of overlapping siblings the later one is under the pointer`() {
        val home = Node("home tile", clickable = true)
        val panel = Node("panel row", clickable = true)
        val root = Node("root", children = listOf(home, panel))
        assertSame(panel, tree.deepest(root) { it.clickable })
    }

    @Test
    fun `the innermost one that takes it, where the pointer is`() {
        val button = Node("button", clickable = true)
        val elsewhere = Node("elsewhere", hit = false, clickable = true)
        val card = Node("card", clickable = true, children = listOf(button, elsewhere))
        assertSame(button, tree.deepest(Node("root", children = listOf(card))) { it.clickable })
        assertNull(tree.deepest(Node("root", hit = false, children = listOf(card))) { it.clickable })
    }

    @Test
    fun `a scroll goes to the page along its axis, past a row across it`() {
        val row = Node("row", axisless = false)
        val rowAcross = Node("row across", children = listOf(row))
        val page = Node("page", along = true, children = listOf(rowAcross))
        val target = tree.scrollTarget(listOf(Node("root", children = listOf(page))), { it.along }, { it.axisless })
        assertEquals(ScrollTarget.Along(page), target)
    }

    @Test
    fun `without a list along the axis, one that doesn't say its axis, kept out of the pool`() {
        val old = Node("old list", axisless = true)
        val target = tree.scrollTarget(listOf(Node("root", children = listOf(old))), { it.along }, { it.axisless })
        assertEquals(ScrollTarget.Axisless(old), target)
        assertTrue("old list" !in tree.released)
    }

    @Test
    fun `a window under the topmost one is not looked at when the topmost has a list`() {
        val top = Node("top", children = listOf(Node("top list", axisless = true)))
        val below = Node("below", children = listOf(Node("below list", along = true)))
        val target = tree.scrollTarget(listOf(top, below), { it.along }, { it.axisless })
        assertEquals("top list", target?.node?.name)
    }

    @Test
    fun `what the walk passed goes back`() {
        val a = Node("a")
        val b = Node("b")
        tree.deepest(Node("root", children = listOf(a, b))) { false }
        assertEquals(listOf("b", "a"), tree.released)
    }
}
