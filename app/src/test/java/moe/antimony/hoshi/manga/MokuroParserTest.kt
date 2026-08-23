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

    // Single-page excerpt from a real mokuro 0.2.0-beta.6 output file (verified by rendering its
    // block boxes back over the source page image and confirming visual alignment with the real
    // speech bubbles/text areas -- see conversation notes). Real output never included
    // lines_coords for this volume, confirming the axis-aligned-box-subdivision fallback is the
    // common case, not a rare one, and exercises the extra "chars" volume-level field and
    // decimal font_size values real mokuro emits.
    @Test
    fun parsesRealMokuroVolumeExcerptWithoutLinesCoords() {
        val volume = MokuroParser.parse(realMokuroPageFixtureJson)

        assertEquals("0.2.0-beta.6", volume.version)
        assertEquals("All You Need Is Kill", volume.title)
        assertEquals(15989, volume.chars)
        val page = volume.pages.single()
        assertEquals(761, page.imgWidth)
        assertEquals(1200, page.imgHeight)
        assertEquals(12, page.blocks.size)
        assertTrue(page.blocks.all { it.linesCoords.isEmpty() })
        val firstBlock = page.blocks.first()
        assertEquals(MokuroRect(621.0, 127.0, 669.0, 205.0), firstBlock.rect)
        assertEquals(listOf("これ", "サインな"), firstBlock.lines)
        val proseBlock = page.blocks[5]
        assertEquals(false, proseBlock.vertical)
        assertEquals(15.5, proseBlock.fontSize)
    }
}

private val realMokuroPageFixtureJson = """
{
  "version": "0.2.0-beta.6",
  "title": "All You Need Is Kill",
  "title_uuid": "a645c2a4-9b7b-5441-9ed7-a38402f2f11e",
  "volume": "オール・ユー・ニード・イズ・キル 第01巻",
  "volume_uuid": "ebe8e9c6-5f08-4def-bea4-5b2be4dc5ba8",
  "chars": 15989,
  "pages": [
    {
      "version": "0.1.6",
      "img_width": 761,
      "img_height": 1200,
      "img_path": "オール・ユー・ニード・イズ・キル 第01巻/オール・ユー・ニード・イズ・キル_1_009.webp",
      "blocks": [
        { "box": [621, 127, 669, 205], "vertical": true, "font_size": 24, "lines": ["これ", "サインな"] },
        { "box": [303, 130, 351, 192], "vertical": true, "font_size": 26, "lines": ["ああ．．．", "はい．．"] },
        { "box": [37, 113, 109, 297], "vertical": true, "font_size": 22, "lines": ["いやっぱり", "緊張してんのかな．．．"] },
        { "box": [275, 420, 362, 596], "vertical": true, "font_size": 22, "lines": ["初出撃前に", "夢で２階級特進とか", "勘弁．．．"] },
        { "box": [37, 430, 111, 488], "vertical": true, "font_size": 32, "lines": ["ん．．．あれ", "．．．？"] },
        { "box": [62, 562, 209, 604], "vertical": false, "font_size": 15.5, "lines": ["本題かにも己の", "保湿力については、公園には入し大量の運動以外の"] },
        { "box": [643, 643, 698, 758], "vertical": true, "font_size": 24, "lines": ["僕これ", "書きましたよ"] },
        { "box": [576, 833, 625, 936], "vertical": true, "font_size": 20, "lines": ["何言ってんだ", "早く書けよ"] },
        { "box": [431, 646, 503, 793], "vertical": true, "font_size": 22, "lines": ["「自分が酒保から", "酒を盗みました」", "ってな"] },
        { "box": [384, 807, 485, 964], "vertical": true, "font_size": 22, "lines": ["今から", "がっぱらってきて", "今晩は", "景気づけだ"] },
        { "box": [175, 1079, 344, 1107], "vertical": false, "font_size": 20, "lines": ["ヨナバル・ジ・"] },
        { "box": [35, 882, 134, 1051], "vertical": true, "font_size": 19, "lines": ["作戦終了後に", "死んだ奴が書いたのを", "提出すれば", "皆幸せーーだろ？"] }
      ]
    }
  ]
}
""".trimIndent()
