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
