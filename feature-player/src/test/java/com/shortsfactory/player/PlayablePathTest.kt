package com.shortsfactory.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayablePathTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun root() = tmp.newFolder("files")

    @Test
    fun regularFileInsideRootIsAllowed() {
        val root = root()
        val file = File(root, "a.mp4").apply { writeText("x") }
        assertTrue(PlayablePath.isInside(file.path, listOf(root)))
    }

    @Test
    fun fileInNestedFolderIsAllowed() {
        val root = root()
        val file = File(File(root, "exports/1").apply { mkdirs() }, "a.mp4").apply { writeText("x") }
        assertTrue(PlayablePath.isInside(file.path, listOf(root)))
    }

    @Test
    fun fileOutsideRootsIsRejected() {
        val root = root()
        val outside = tmp.newFile("outside.mp4")
        assertFalse(PlayablePath.isInside(outside.path, listOf(root)))
    }

    @Test
    fun parentTraversalIsRejected() {
        val root = root()
        val outside = tmp.newFile("outside.mp4")
        assertFalse(PlayablePath.isInside(root.path + "/../" + outside.name, listOf(root)))
    }

    @Test
    fun siblingWithSamePrefixIsRejected() {
        val root = root()
        val sibling = tmp.newFolder("files-evil")
        val file = File(sibling, "a.mp4").apply { writeText("x") }
        assertFalse(PlayablePath.isInside(file.path, listOf(root)))
    }

    @Test
    fun missingFileAndDirectoryAreRejected() {
        val root = root()
        assertFalse(PlayablePath.isInside(File(root, "none.mp4").path, listOf(root)))
        assertFalse(PlayablePath.isInside(root.path, listOf(root)))
    }
}
