package moe.antimony.hoshi.features.furigana

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Document
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * Rewrites an XHTML chapter document, wrapping unknown words in `<ruby>...<rt>` furigana, by
 * walking every text node not already inside ruby/rt/rp/script/style and running [segment] over
 * its text. Only the kanji portions of a word get a reading -- see [FuriganaWordSpan.furiganaParts]
 * for how those are isolated from a conjugated dictionary match, and for why a kana-only word
 * (where there's no kanji to isolate) ends up not wrapped at all.
 *
 * Parsing is XML-strict (chapter files are XHTML), but unlike the package/container XML parsing
 * in EpubBookParser, DOCTYPE declarations are allowed here rather than rejected outright: many
 * real-world EPUBs declare a DOCTYPE specifically to define entities like `&nbsp;`, and rejecting
 * it would silently disable furigana on those books. External entity resolution stays disabled
 * either way, so this doesn't reopen the XXE hole that restriction exists to close.
 *
 * Malformed XHTML (or any other parse/serialize failure) falls back to returning [html]
 * unchanged -- this transform only ever adds markup, so "do nothing" is always a safe fallback.
 *
 * This runs synchronously on the WebView's resource-loading thread, once per chapter load, and
 * is on the critical path for the chapter to appear -- each dictionary lookup costs a few
 * milliseconds, and a real chapter can run to many thousands of characters. [maxProcessedCharacters]
 * bounds that: once this many characters of text have been run through [segment], the rest of the
 * chapter is left as plain, unannotated text rather than continuing to burn lookups the reader is
 * now blocked on. (A live, incrementally-applied version that covered a whole book regardless of
 * length was tried and reverted -- offset bookkeeping between the native document and the
 * already-rendered page drifted in ways that misplaced furigana elsewhere in the text -- so for
 * now this trades full coverage for correctness and simplicity.)
 *
 * That budget is spent starting from [startFraction] of the way through the chapter's text
 * (backed off by [backBufferCharacters]), not always from the very beginning -- passing the
 * reader's actual current progress means a book with no chapter breaks (where the "chapter" is
 * the entire book, and the budget alone could never reach very far into it) still gets furigana
 * around wherever the reader actually is, rather than only ever near the start. [startFraction]
 * reflects progress into the chapter, which in practice lands closer to the *end* of the
 * currently visible page than its start (page turns update it forward as you read), so
 * [backBufferCharacters] needs to be generous enough to reach back across a whole page, not just
 * a few words -- too small, and re-running this (e.g. the reader-facing "recalculate" action)
 * only ever covers the *next* page, never the one currently on screen.
 *
 * [onProgress] is called with (characters processed so far, total characters this call intends
 * to process) as work happens, and once more at the end with processed == total -- this runs
 * while the chapter is still loading (nothing else to show yet), so a caller can surface it as
 * "1,314 / 3,000 characters scanned" instead of an indefinite spinner that looks the same whether
 * it's about to finish or has actually hung.
 */
internal class FuriganaHtmlInjector(
    private val maxProcessedCharacters: Int = 3_000,
    private val backBufferCharacters: Int = 1_500,
    private val onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    private val segment: (String) -> List<FuriganaWordSpan>,
) {
    private var skipRemaining = 0
    private var processedCharacters = 0
    private var targetCharacters = 0

    fun inject(html: String, startFraction: Double = 0.0): String {
        val document = runCatching { parse(html) }.getOrNull() ?: return html
        val root = document.documentElement ?: return html
        val totalLength = runCatching { countTextLength(root) }.getOrDefault(0)
        val windowStart = (totalLength * startFraction.coerceIn(0.0, 1.0)).toInt() - backBufferCharacters
        skipRemaining = windowStart.coerceIn(0, totalLength)
        processedCharacters = 0
        targetCharacters = (totalLength - skipRemaining).coerceIn(0, maxProcessedCharacters)
        onProgress(0, targetCharacters)
        runCatching { walk(root) }.getOrElse {
            onProgress(targetCharacters, targetCharacters)
            return html
        }
        onProgress(targetCharacters, targetCharacters)
        return runCatching { serialize(document) }.getOrNull() ?: html
    }

    private fun countTextLength(node: Node): Int {
        var total = 0
        var child = node.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.TEXT_NODE -> total += child.nodeValue?.length ?: 0
                Node.ELEMENT_NODE -> if (child.localName?.lowercase() !in skippedElementTags) total += countTextLength(child)
            }
            child = child.nextSibling
        }
        return total
    }

    private fun walk(node: Node) {
        var child = node.firstChild
        while (child != null) {
            if (skipRemaining <= 0 && processedCharacters >= maxProcessedCharacters) return
            val next = child.nextSibling
            when (child.nodeType) {
                Node.TEXT_NODE -> handleTextNode(child)
                Node.ELEMENT_NODE -> if (child.localName?.lowercase() !in skippedElementTags) walk(child)
            }
            child = next
        }
    }

    private fun handleTextNode(textNode: Node) {
        val text = textNode.nodeValue
        if (text.isNullOrBlank()) return
        if (skipRemaining > 0) {
            // Skipped nodes don't count toward the processing budget -- only text actually run
            // through the (expensive) dictionary lookups does.
            skipRemaining -= text.length
            return
        }
        replaceTextNode(textNode, text)
    }

    private fun replaceTextNode(textNode: Node, text: String) {
        processedCharacters += text.length
        onProgress(processedCharacters.coerceAtMost(targetCharacters), targetCharacters)
        val spans = segment(text)
        if (spans.none { it.furiganaParts() != null }) return

        val document = textNode.ownerDocument ?: return
        val parent = textNode.parentNode ?: return
        for (span in spans) {
            val parts = span.furiganaParts()
            if (parts == null) {
                parent.insertBefore(document.createTextNode(span.text), textNode)
                continue
            }
            for (part in parts) {
                val node = when (part) {
                    is FuriganaPart.Plain -> document.createTextNode(part.text)
                    is FuriganaPart.Ruby -> document.createElement("ruby").apply {
                        appendChild(document.createTextNode(part.kanjiText))
                        appendChild(
                            document.createElement("rt").apply { appendChild(document.createTextNode(part.reading)) },
                        )
                    }
                }
                parent.insertBefore(node, textNode)
            }
        }
        parent.removeChild(textNode)
    }

    private fun parse(html: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(html)))
    }

    private fun serialize(document: Document): String {
        val writer = StringWriter()
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    private companion object {
        val skippedElementTags = setOf("ruby", "rt", "rp", "script", "style")
    }
}
