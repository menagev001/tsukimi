package moe.antimony.hoshi.manga

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MokuroParserTest {
    @Test
    fun parsesConfirmedMokuroJsonSchema() {
        val json = """
            {
                "version": "0.2.9",
                "title": "Example Title",
                "title_uuid": "title-uuid",
                "volume": "Volume 1",
                "volume_uuid": "volume-uuid",
                "pages": [
                    {
                        "img_width": 1500,
                        "img_height": 2200,
                        "img_path": "001.jpg",
                        "blocks": [
                            {
                                "box": [10.0, 20.0, 110.0, 220.0],
                                "vertical": true,
                                "font_size": 24.0,
                                "lines_coords": [
                                    [[10.0, 20.0], [110.0, 20.0], [110.0, 120.0], [10.0, 120.0]],
                                    [[10.0, 120.0], [110.0, 120.0], [110.0, 220.0], [10.0, 220.0]]
                                ],
                                "lines": ["一行目", "二行目"]
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val volume = MokuroParser.parse(json)

        assertEquals("Example Title", volume.title)
        assertEquals("title-uuid", volume.titleUuid)
        assertEquals("Volume 1", volume.volume)
        assertEquals("volume-uuid", volume.volumeUuid)
        val page = volume.pages.single()
        assertEquals(1500, page.imgWidth)
        assertEquals(2200, page.imgHeight)
        assertEquals("001.jpg", page.imgPath)
        val block = page.blocks.single()
        assertEquals(true, block.vertical)
        assertEquals(24.0, block.fontSize)
        assertEquals(listOf("一行目", "二行目"), block.lines)
        assertEquals(MokuroRect(10.0, 20.0, 110.0, 220.0), block.rect)
        assertEquals(MokuroRect(10.0, 20.0, 110.0, 120.0), block.linesCoords[0].toMokuroRect())
    }

    @Test
    fun parsesFromFile() {
        val file = Files.createTempFile("mokuro", ".json").toFile()
        file.writeText("""{"pages": [{"img_width": 100, "img_height": 200, "img_path": "a.jpg"}]}""")

        val volume = MokuroParser.parse(file)

        assertEquals(1, volume.pages.size)
        assertEquals("a.jpg", volume.pages.single().imgPath)
    }

    @Test
    fun toleratesMissingOptionalTopLevelFields() {
        val volume = MokuroParser.parse("""{"pages": []}""")

        assertEquals(null, volume.title)
        assertEquals(null, volume.version)
        assertTrue(volume.pages.isEmpty())
    }

    @Test
    fun ignoresUnknownKeys() {
        val volume = MokuroParser.parse(
            """{"pages": [], "some_future_field": {"nested": true}}""",
        )

        assertTrue(volume.pages.isEmpty())
    }

    @Test
    fun blockRectIsNullWhenBoxIsMalformed() {
        val volume = MokuroParser.parse(
            """
            {
                "pages": [
                    {
                        "img_width": 100, "img_height": 200, "img_path": "a.jpg",
                        "blocks": [
                            {"box": [1.0, 2.0], "lines": ["x"]},
                            {"box": [], "lines": ["y"]}
                        ]
                    }
                ]
            }
            """.trimIndent(),
        )

        val blocks = volume.pages.single().blocks
        assertNull(blocks[0].rect)
        assertNull(blocks[1].rect)
    }

    @Test
    fun toleratesLinesCoordsLengthMismatchWithLines() {
        val volume = MokuroParser.parse(
            """
            {
                "pages": [
                    {
                        "img_width": 100, "img_height": 200, "img_path": "a.jpg",
                        "blocks": [
                            {"box": [0.0, 0.0, 10.0, 10.0], "lines": ["one", "two"], "lines_coords": [[[0.0, 0.0]]]}
                        ]
                    }
                ]
            }
            """.trimIndent(),
        )

        val block = volume.pages.single().blocks.single()
        assertEquals(2, block.lines.size)
        assertEquals(1, block.linesCoords.size)
    }

    @Test
    fun emptyPointListHasNoBoundingBox() {
        assertNull(emptyList<List<Double>>().toMokuroRect())
    }
}
