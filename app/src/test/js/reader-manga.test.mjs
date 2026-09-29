import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const readerMangaUrl = new URL('../../main/assets/hoshi-web/manga/reader-manga.js', import.meta.url);

function loadMangaGeometry() {
    const source = fs.readFileSync(readerMangaUrl, 'utf8');
    const window = {};
    vm.runInNewContext(source, { window });
    return window.hoshiMangaGeometry;
}

// vm.runInNewContext objects belong to a different realm than this test file's, so
// assert.deepEqual (strict) fails on prototype identity even when structurally equal --
// round-tripping through JSON normalizes both sides into this realm's plain objects/arrays.
function plain(value) {
    return JSON.parse(JSON.stringify(value));
}

test('fitDisplaySize scales to fit inside the viewport preserving aspect ratio', () => {
    const geometry = loadMangaGeometry();

    const fit = geometry.fitDisplaySize(761, 1200, 400, 900);

    // width-constrained: 400/761 < 900/1200
    assert.equal(fit.scale, 400 / 761);
    assert.ok(Math.abs(fit.width - 400) < 1e-9);
    assert.ok(Math.abs(fit.height - 1200 * (400 / 761)) < 1e-9);
});

test('fitDisplaySize is height-constrained for a tall narrow viewport', () => {
    const geometry = loadMangaGeometry();

    const fit = geometry.fitDisplaySize(1692, 2400, 2000, 1000);

    assert.equal(fit.scale, 1000 / 2400);
});

test('fitSpreadDisplaySize with one page matches fitDisplaySize exactly', () => {
    const geometry = loadMangaGeometry();

    const single = geometry.fitDisplaySize(761, 1200, 400, 900);
    const spread = geometry.fitSpreadDisplaySize([{ width: 761, height: 1200 }], 400, 900);

    assert.equal(spread.scale, single.scale);
    assert.ok(Math.abs(spread.totalWidth - single.width) < 1e-9);
    assert.ok(Math.abs(spread.totalHeight - single.height) < 1e-9);
    assert.equal(spread.widths.length, 1);
    assert.ok(Math.abs(spread.widths[0] - single.width) < 1e-9);
});

test('fitSpreadDisplaySize scales two same-sized pages to a shared height and sums their widths', () => {
    const geometry = loadMangaGeometry();

    // Two same-sized pages in a viewport chosen to be width-constrained: at viewport height
    // (2000), the pair would need width 1000*(800/1200)*2 = 1333.3, well over the 1000 available,
    // so the pair's total width is capped to the viewport width instead.
    const fit = geometry.fitSpreadDisplaySize(
        [{ width: 800, height: 1200 }, { width: 800, height: 1200 }],
        1000,
        2000,
    );

    assert.ok(Math.abs(fit.totalWidth - 1000) < 1e-9);
    assert.ok(fit.totalHeight < 2000);
    assert.ok(Math.abs(fit.heights[0] - fit.totalHeight) < 1e-9);
    assert.ok(Math.abs(fit.heights[1] - fit.totalHeight) < 1e-9);
    assert.equal(fit.widths.length, 2);
    assert.ok(Math.abs(fit.widths[0] - 500) < 1e-9);
    assert.ok(Math.abs(fit.widths[1] - 500) < 1e-9);
});

test('fitSpreadDisplaySize gives every page in a spread the same display height even when their natural sizes differ', () => {
    const geometry = loadMangaGeometry();

    // A taller second page (e.g. a slightly different scan) must still render at the SAME
    // height as the first -- print convention, and what makes a spread look like one unit
    // instead of two mismatched pages. Each page gets its OWN scale factor to get there.
    const fit = geometry.fitSpreadDisplaySize(
        [{ width: 800, height: 1200 }, { width: 800, height: 1600 }],
        3000,
        3000,
    );

    assert.ok(Math.abs(fit.heights[0] - fit.heights[1]) < 1e-9);
    assert.notEqual(fit.scales[0], fit.scales[1]);
    assert.ok(Math.abs(fit.widths[0] - 800 * fit.scales[0]) < 1e-9);
    assert.ok(Math.abs(fit.widths[1] - 800 * fit.scales[1]) < 1e-9);
    assert.ok(Math.abs(fit.totalWidth - (fit.widths[0] + fit.widths[1])) < 1e-9);
});

test('blockFontSizePx uses the OCR-reported font_size scaled to the current display scale', () => {
    const geometry = loadMangaGeometry();
    const block = { box: [0, 0, 100, 300], vertical: true, lines: ['a', 'b'], font_size: 24 };

    assert.equal(geometry.blockFontSizePx(block, 0.5), 12);
    assert.equal(geometry.blockFontSizePx(block, 2), 48);
});

test('blockFontSizePx falls back to a box/line-count estimate when font_size is missing', () => {
    const geometry = loadMangaGeometry();
    // Vertical block: font size derived from width (the narrow dimension text lays out across).
    const verticalBlock = { box: [0, 0, 100, 300], vertical: true, lines: ['a', 'b'] };
    // Horizontal block: derived from height instead.
    const horizontalBlock = { box: [0, 0, 300, 100], vertical: false, lines: ['a', 'b'] };

    assert.ok(Math.abs(geometry.blockFontSizePx(verticalBlock, 1) - (100 / 2) * 0.9) < 1e-9);
    assert.ok(Math.abs(geometry.blockFontSizePx(horizontalBlock, 1) - (100 / 2) * 0.9) < 1e-9);
});

test('blockFontSizePx never returns a size too small to hit-test', () => {
    const geometry = loadMangaGeometry();
    const tinyBlock = { box: [0, 0, 1, 1], vertical: true, lines: ['a', 'b', 'c', 'd', 'e', 'f'] };

    assert.ok(geometry.blockFontSizePx(tinyBlock, 0.01) >= 6);
});

test('axisAlignedBoxFromPoints computes the bounding box of a skewed polygon', () => {
    const geometry = loadMangaGeometry();

    // Real mokuro lines_coords sample -- a slightly skewed quadrilateral, not axis-aligned.
    const box = geometry.axisAlignedBoxFromPoints([
        [1321, 203], [1361, 203], [1366, 431], [1326, 431],
    ]);

    assert.deepEqual(plain(box), { x1: 1321, y1: 203, x2: 1366, y2: 431 });
});

test('axisAlignedBoxFromPoints returns null for empty or missing input', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.axisAlignedBoxFromPoints([]), null);
    assert.equal(geometry.axisAlignedBoxFromPoints(null), null);
});

test('lineRects uses lines_coords bounding boxes when present and length-matched', () => {
    const geometry = loadMangaGeometry();
    const block = {
        box: [1277, 199, 1462, 433],
        vertical: true,
        lines: ['a', 'b'],
        lines_coords: [
            [[1422, 206], [1462, 206], [1462, 407], [1422, 407]],
            [[1277, 199], [1317, 199], [1317, 335], [1277, 335]],
        ],
    };

    const rects = geometry.lineRects(block);

    assert.deepEqual(plain(rects[0]), { x1: 1422, y1: 206, x2: 1462, y2: 407 });
    assert.deepEqual(plain(rects[1]), { x1: 1277, y1: 199, x2: 1317, y2: 335 });
});

test('lineRects falls back to even right-to-left column subdivision when lines_coords is absent', () => {
    // Matches the real mokuro 0.2.0-beta.6 excerpt verified by rendering it back over the
    // source page image (see MokuroParserTest's real-data fixture / conversation notes) --
    // this is the common case: real-world mokuro output usually omits lines_coords entirely.
    const geometry = loadMangaGeometry();
    const block = { box: [37, 113, 109, 297], vertical: true, lines: ['いやっぱり', '緊張してんのかな．．．'] };

    const rects = geometry.lineRects(block);

    assert.equal(rects.length, 2);
    // First line (rightmost column, read first in vertical Japanese text).
    assert.deepEqual(plain(rects[0]), { x1: 73, y1: 113, x2: 109, y2: 297 });
    assert.deepEqual(plain(rects[1]), { x1: 37, y1: 113, x2: 73, y2: 297 });
});

test('lineRects falls back to even top-to-bottom row subdivision for horizontal blocks', () => {
    const geometry = loadMangaGeometry();
    const block = { box: [0, 0, 100, 40], vertical: false, lines: ['one', 'two'] };

    const rects = geometry.lineRects(block);

    assert.deepEqual(plain(rects[0]), { x1: 0, y1: 0, x2: 100, y2: 20 });
    assert.deepEqual(plain(rects[1]), { x1: 0, y1: 20, x2: 100, y2: 40 });
});

test('lineRects ignores a lines_coords length mismatch and falls back to subdivision', () => {
    const geometry = loadMangaGeometry();
    const block = {
        box: [0, 0, 100, 30],
        vertical: false,
        lines: ['one', 'two', 'three'],
        lines_coords: [[[0, 0], [10, 0], [10, 10], [0, 10]]],
    };

    const rects = geometry.lineRects(block);

    assert.equal(rects.length, 3);
    assert.deepEqual(plain(rects[0]), { x1: 0, y1: 0, x2: 100, y2: 10 });
});

// --- tap snapping -----------------------------------------------------------------------------
// The overlay's glyphs are synthesized from OCR boxes in a typeface that is not the printed one,
// so they never sit exactly on the artwork. These cover the geometry that makes a near-miss land
// on the intended character anyway (see selectTextAt in reader-manga.js).

function rect(left, top, width, height) {
    return { left, top, width, height, right: left + width, bottom: top + height };
}

test('rectDistance is zero anywhere inside a rect, including its edges', () => {
    const geometry = loadMangaGeometry();
    const r = rect(10, 20, 100, 50);

    assert.equal(geometry.rectDistance(r, 60, 45), 0);
    assert.equal(geometry.rectDistance(r, 10, 20), 0);
    assert.equal(geometry.rectDistance(r, 110, 70), 0);
});

test('rectDistance measures the gap to the nearest edge, diagonally at the corners', () => {
    const geometry = loadMangaGeometry();
    const r = rect(10, 20, 100, 50);

    assert.equal(geometry.rectDistance(r, 130, 45), 20);
    assert.equal(geometry.rectDistance(r, 60, 5), 15);
    // Off the bottom-right corner: a 3-4-5 triangle.
    assert.equal(geometry.rectDistance(r, 113, 74), 5);
});

test('nearestRectIndex picks the containing rect over a merely close one', () => {
    const geometry = loadMangaGeometry();
    const rects = [rect(0, 0, 10, 10), rect(100, 100, 40, 40), rect(145, 100, 40, 40)];

    assert.equal(geometry.nearestRectIndex(rects, 120, 120, 24), 1);
});

test('nearestRectIndex snaps to a block the tap narrowly missed', () => {
    const geometry = loadMangaGeometry();
    const rects = [rect(0, 0, 10, 10), rect(100, 100, 40, 40)];

    // 8px past the right edge -- inside the finger-slop tolerance.
    assert.equal(geometry.nearestRectIndex(rects, 148, 120, 24), 1);
});

test('nearestRectIndex rejects a tap beyond the tolerance so blank art stays blank', () => {
    const geometry = loadMangaGeometry();
    const rects = [rect(100, 100, 40, 40)];

    // 60px clear of every block: this is the tap that should still reset the zoom.
    assert.equal(geometry.nearestRectIndex(rects, 200, 120, 24), -1);
});

test('nearestRectIndex returns -1 when there are no blocks at all', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.nearestRectIndex([], 50, 50, 24), -1);
});

test('nearestRectCenter returns null when the point is already on a glyph', () => {
    const geometry = loadMangaGeometry();
    const rects = [rect(0, 0, 20, 20), rect(0, 20, 20, 20)];

    // Direct hit -- the original selectText call was accurate, so a miss was not bad aim and
    // must not be retried (that would defeat tap-again-to-dismiss).
    assert.equal(geometry.nearestRectCenter(rects, 10, 30), null);
});

test('nearestRectCenter returns the centre of the closest character rect', () => {
    const geometry = loadMangaGeometry();
    const rects = [rect(0, 0, 20, 20), rect(0, 30, 20, 20), rect(0, 60, 20, 20)];

    // In the gap between the second and third glyph of a vertical column, 4px below the second
    // and 6px above the third -- the kind of inter-character miss the overlay's drift produces.
    assert.deepEqual(plain(geometry.nearestRectCenter(rects, 10, 54)), { x: 10, y: 40 });
});

test('nearestRectCenter ignores zero-area rects from collapsed ranges', () => {
    const geometry = loadMangaGeometry();
    const rects = [rect(0, 0, 0, 0), rect(100, 100, 20, 20)];

    assert.deepEqual(plain(geometry.nearestRectCenter(rects, 5, 5)), { x: 110, y: 110 });
});

test('nearestRectCenter returns null when a block has no usable glyph rects', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.nearestRectCenter([rect(0, 0, 0, 0)], 5, 5), null);
});

// --- font fitting -----------------------------------------------------------------------------
// Japanese glyphs are full-width (one em of advance each), so a line's font size is pinned by its
// own extent divided by its own character count. Getting this wrong is what left the last
// character of a line sitting outside the overlay and therefore untappable.

test('glyphCount counts code points, not UTF-16 units', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.glyphCount('ねこ'), 2);
    // A supplementary-plane kanji is one glyph despite being a surrogate pair.
    assert.equal(geometry.glyphCount('\u{20BB7}'), 1);
    assert.equal(geometry.glyphCount('ね\u{20BB7}こ'), 3);
    assert.equal(geometry.glyphCount(''), 0);
});

test('longestLineGlyphCount reports the longest line in a block', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.longestLineGlyphCount(['あ', 'あいうえお', 'あいう']), 5);
    assert.equal(geometry.longestLineGlyphCount([]), 0);
});

test('fittedFontSizePx makes an n-character run exactly span its extent', () => {
    const geometry = loadMangaGeometry();

    // 5 characters down a 100px-tall column 20px wide: one em per character is exactly 20px, so
    // the run ends precisely at the bottom of the OCR line rather than short of it.
    assert.equal(geometry.fittedFontSizePx(100, 20, 5), 20);
});

test('fittedFontSizePx caps at the cross axis so a merged OCR box cannot blow up', () => {
    const geometry = loadMangaGeometry();

    // A tall box holding only two characters would imply a 60px font; the 14px column width is
    // the real constraint.
    assert.equal(geometry.fittedFontSizePx(120, 14, 2), 14);
});

test('fittedFontSizePx ignores a non-positive cross axis instead of collapsing', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.fittedFontSizePx(100, 0, 4), 25);
});

test('fittedFontSizePx never returns a size too small to hit-test', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.fittedFontSizePx(10, 2, 40), 6);
});

test('fittedFontSizePx returns 0 for degenerate geometry so the caller can fall back', () => {
    const geometry = loadMangaGeometry();

    assert.equal(geometry.fittedFontSizePx(100, 20, 0), 0);
    assert.equal(geometry.fittedFontSizePx(0, 20, 5), 0);
});

// --- block reveal -----------------------------------------------------------------------------
// Pages show clean artwork; a first tap on an OCR block only reveals it as readable text, and
// lookups happen on the revealed text (see blockTapAction in reader-manga.js).

function loadMangaTap() {
    const source = fs.readFileSync(readerMangaUrl, 'utf8');
    const window = {};
    vm.runInNewContext(source, { window });
    return window.hoshiMangaTap;
}

test('blockTapAction reveals a block on the first single tap instead of looking anything up', () => {
    const tap = loadMangaTap();
    const a = {};

    assert.equal(tap.blockTapAction(a, null, false), 'reveal');
});

test('blockTapAction looks up text on the already-revealed block', () => {
    const tap = loadMangaTap();
    const a = {};

    assert.equal(tap.blockTapAction(a, a, false), 'select');
});

test('blockTapAction moves the reveal to another block without looking anything up', () => {
    const tap = loadMangaTap();
    const a = {};
    const b = {};

    assert.equal(tap.blockTapAction(b, a, false), 'reveal');
});

test('blockTapAction only hides the revealed block on a single tap clear of every block', () => {
    const tap = loadMangaTap();

    // Not 'blank': closing the block must not also turn the page or reset the zoom.
    assert.equal(tap.blockTapAction(null, {}, false), 'dismiss');
});

test('blockTapAction leaves a blank tap blank when nothing is revealed', () => {
    const tap = loadMangaTap();

    assert.equal(tap.blockTapAction(null, null, false), 'blank');
});

test('blockTapAction keeps double-tap sentence lookup and page turns independent of the reveal', () => {
    const tap = loadMangaTap();
    const a = {};

    assert.equal(tap.blockTapAction(a, null, true), 'select');
    assert.equal(tap.blockTapAction(a, {}, true), 'select');
    assert.equal(tap.blockTapAction(null, a, true), 'blank');
});

test('normalizedCrop treats missing or invalid edges as uncropped', () => {
    const geometry = loadMangaGeometry();

    assert.deepEqual(plain(geometry.normalizedCrop(null)), { left: 0, top: 0, right: 0, bottom: 0 });
    assert.deepEqual(
        plain(geometry.normalizedCrop({ left: 0.1, top: 'x', right: -0.2, bottom: NaN })),
        { left: 0.1, top: 0, right: 0, bottom: 0 },
    );
});

test('normalizedCrop always leaves part of the page visible', () => {
    const geometry = loadMangaGeometry();

    const crop = geometry.normalizedCrop({ left: 0.45, top: 0.9, right: 0.45, bottom: 0.45 });

    assert.ok(1 - crop.left - crop.right >= 0.1 - 1e-9);
    assert.ok(1 - crop.top - crop.bottom >= 0.1 - 1e-9);
    assert.ok(Math.abs(crop.left - crop.right) < 1e-9);
});

test('a cropped page is fitted larger than the same page uncropped', () => {
    const geometry = loadMangaGeometry();
    const page = { width: 869, height: 1240 };
    const crop = { left: 0.08, top: 0.06, right: 0.1, bottom: 0.07 };

    const uncropped = geometry.fitSpreadDisplaySize([page], 1264, 1680);
    const cropped = geometry.fitSpreadDisplaySize([geometry.croppedPageSize(page, crop)], 1264, 1680);

    assert.ok(cropped.scale > uncropped.scale);
    assert.ok(cropped.totalHeight <= 1680 + 1e-9 && cropped.totalWidth <= 1264 + 1e-9);
});

test('pageCropLayout shifts the full page so the crop origin lands on the slot origin', () => {
    const geometry = loadMangaGeometry();

    const layout = geometry.pageCropLayout(
        { width: 1000, height: 2000 },
        { left: 0.1, top: 0.05, right: 0.2, bottom: 0.15 },
        0.5,
    );

    assert.ok(Math.abs(layout.slotWidth - 1000 * 0.7 * 0.5) < 1e-9);
    assert.ok(Math.abs(layout.slotHeight - 2000 * 0.8 * 0.5) < 1e-9);
    assert.ok(Math.abs(layout.imageLeft - -50) < 1e-9);
    assert.ok(Math.abs(layout.imageTop - -50) < 1e-9);
    assert.equal(layout.imageWidth, 500);
    assert.equal(layout.imageHeight, 1000);
});

test('visibleRect keeps only the part of a block inside its slot', () => {
    const geometry = loadMangaGeometry();
    const slot = { left: 0, top: 0, right: 100, bottom: 100 };

    assert.deepEqual(
        plain(geometry.visibleRect({ left: 80, top: 10, right: 120, bottom: 30 }, slot)),
        { left: 80, top: 10, right: 100, bottom: 30 },
    );
    assert.equal(geometry.visibleRect({ left: 110, top: 10, right: 130, bottom: 30 }, slot).left, Infinity);
});
