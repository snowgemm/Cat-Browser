package io.github.snowgemm.catbrowser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesParserTest {
    @Test
    fun parsesHeadingsBulletsAndParagraphs() {
        val blocks =
            parseReleaseNotes(
                """
                ## Cat Browser 0.2.8

                ### New Features
                - Add page sharing
                - Add bookmark folders

                Release notes are generated from commits.
                """.trimIndent(),
            )

        assertEquals(ReleaseNoteBlockType.HEADING, blocks[0].type)
        assertEquals("Cat Browser 0.2.8", blocks[0].text)
        assertEquals(ReleaseNoteBlockType.SPACER, blocks[1].type)
        assertEquals(ReleaseNoteBlockType.HEADING, blocks[2].type)
        assertEquals(ReleaseNoteBlockType.BULLET, blocks[3].type)
        assertEquals("Add page sharing", blocks[3].text)
        assertEquals("•", blocks[3].marker)
        assertEquals(ReleaseNoteBlockType.PARAGRAPH, blocks.last().type)
    }

    @Test
    fun preservesOrderedListMarkersAndIndentation() {
        val blocks = parseReleaseNotes("1. First step\n  - Nested detail\n2) Second step")

        assertEquals("1.", blocks[0].marker)
        assertEquals(1, blocks[1].indent)
        assertEquals("•", blocks[1].marker)
        assertEquals("2)", blocks[2].marker)
    }

    @Test
    fun recognizesEscapesAndMarkdownInlineElementsInOrder() {
        val matches = inlineMarkdownPattern.findAll("\\- **bold** `code` [label](https://example.com)").toList()

        assertEquals(4, matches.size)
        assertEquals("-", matches[0].groups[1]?.value)
        assertEquals("bold", matches[1].groups[2]?.value)
        assertEquals("code", matches[2].groups[3]?.value)
        assertEquals("label", matches[3].groups[5]?.value)
        assertEquals("https://example.com", matches[3].groups[6]?.value)
    }

    @Test
    fun recognizesMarkdownDividersAndTrimsTrailingSpacing() {
        val blocks = parseReleaseNotes("Heading\n---\n\nLast paragraph\n\n")

        assertTrue(blocks.any { it.type == ReleaseNoteBlockType.DIVIDER })
        assertEquals(ReleaseNoteBlockType.PARAGRAPH, blocks.last().type)
    }
}
