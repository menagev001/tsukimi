(function () {
    'use strict';

    // Generalizes fitDisplaySize to 1-2 pages sharing one target display HEIGHT (matching real
    // print convention -- every page in a spread lines up top-to-bottom even if two scans have
    // slightly different natural pixel dimensions), each scaled independently to reach it. A
    // single page is just the n=1 case of the same formula: solving for the largest height that
    // still fits both the viewport height and (at that height) the viewport width reduces
    // exactly to fitDisplaySize's own min(viewportWidth/naturalWidth, viewportHeight/naturalHeight)
    // when there's only one page's width/height ratio in the sum below.
    function fitSpreadDisplaySize(pages, viewportWidth, viewportHeight) {
        var widthToHeightRatioSum = 0;
        for (var i = 0; i < pages.length; i++) {
            widthToHeightRatioSum += pages[i].width / pages[i].height;
        }
        var targetHeight = Math.min(viewportHeight, viewportWidth / widthToHeightRatioSum);
        var widths = [], heights = [], scales = [];
        var totalWidth = 0;
        for (var j = 0; j < pages.length; j++) {
            var pageScale = targetHeight / pages[j].height;
            var width = pages[j].width * pageScale;
            widths.push(width);
            heights.push(targetHeight);
            scales.push(pageScale);
            totalWidth += width;
        }
        return {
            // Only meaningful when every page shares one natural size (true for virtually all
            // real volumes) -- use `scales[i]` for anything that must be exactly correct per page
            // (e.g. overlay positioning below), never this for a multi-page spread.
            scale: scales[0],
            scales: scales,
            totalWidth: totalWidth,
            totalHeight: targetHeight,
            widths: widths,
            heights: heights,
        };
    }

    // Largest share of a page any one edge may hide, and the smallest share of each axis that
    // must stay visible -- a malformed crop can shrink the page, never make it vanish.
    var MAX_CROP_EDGE = 0.45;
    var MIN_VISIBLE_FRACTION = 0.1;

    // Crops arrive as fractions of the page image's own width (left/right) and height
    // (top/bottom). Missing or invalid values mean "no crop" for that edge.
    function normalizedCrop(crop) {
        function edge(value) {
            return typeof value === 'number' && isFinite(value) ? Math.min(MAX_CROP_EDGE, Math.max(0, value)) : 0;
        }
        var c = crop || {};
        var left = edge(c.left), right = edge(c.right), top = edge(c.top), bottom = edge(c.bottom);
        var horizontalExcess = left + right - (1 - MIN_VISIBLE_FRACTION);
        if (horizontalExcess > 0) {
            left -= horizontalExcess / 2;
            right -= horizontalExcess / 2;
        }
        var verticalExcess = top + bottom - (1 - MIN_VISIBLE_FRACTION);
        if (verticalExcess > 0) {
            top -= verticalExcess / 2;
            bottom -= verticalExcess / 2;
        }
        return { left: left, top: top, right: right, bottom: bottom };
    }

    // The part of a page that is fitted to the screen, in the page's natural pixels.
    function croppedPageSize(page, crop) {
        var c = normalizedCrop(crop);
        return {
            width: page.width * (1 - c.left - c.right),
            height: page.height * (1 - c.top - c.bottom),
        };
    }

    // Where the whole page image sits inside its (cropped, clipping) slot once the visible part
    // is scaled by displayScale: the image and the OCR overlay keep full-page coordinates and are
    // just shifted up/left, so cropping never changes any overlay geometry.
    function pageCropLayout(page, crop, displayScale) {
        var c = normalizedCrop(crop);
        var visible = croppedPageSize(page, c);
        return {
            slotWidth: visible.width * displayScale,
            slotHeight: visible.height * displayScale,
            imageLeft: -c.left * page.width * displayScale,
            imageTop: -c.top * page.height * displayScale,
            imageWidth: page.width * displayScale,
            imageHeight: page.height * displayScale,
        };
    }

    function fitDisplaySize(naturalWidth, naturalHeight, viewportWidth, viewportHeight) {
        var spread = fitSpreadDisplaySize([{ width: naturalWidth, height: naturalHeight }], viewportWidth, viewportHeight);
        return { width: spread.totalWidth, height: spread.totalHeight, scale: spread.scale };
    }

    function axisAlignedBoxFromPoints(points) {
        if (!points || !points.length) return null;
        var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
        for (var i = 0; i < points.length; i++) {
            var p = points[i];
            if (!p || p.length < 2) continue;
            var x = p[0], y = p[1];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        if (minX > maxX || minY > maxY) return null;
        return { x1: minX, y1: minY, x2: maxX, y2: maxY };
    }

    function lineRects(block) {
        var box = block.box;
        var n = block.lines.length;
        var rects = [];
        var hasCoords = block.lines_coords && block.lines_coords.length === n;
        for (var i = 0; i < n; i++) {
            if (hasCoords) {
                var r = axisAlignedBoxFromPoints(block.lines_coords[i]);
                if (r) {
                    rects.push(r);
                    continue;
                }
            }
            // Fallback: evenly subdivide the block box into columns (vertical) or rows
            // (horizontal), right-to-left / top-to-bottom to match mokuro's reading order.
            // This is the PRIMARY path for real-world mokuro output, not a rare fallback --
            // most mokuro versions never emit lines_coords at all.
            if (block.vertical) {
                var colWidth = (box[2] - box[0]) / n;
                var cx2 = box[2] - i * colWidth;
                var cx1 = box[2] - (i + 1) * colWidth;
                rects.push({ x1: cx1, y1: box[1], x2: cx2, y2: box[3] });
            } else {
                var rowHeight = (box[3] - box[1]) / n;
                var ry1 = box[1] + i * rowHeight;
                var ry2 = box[1] + (i + 1) * rowHeight;
                rects.push({ x1: box[0], y1: ry1, x2: box[2], y2: ry2 });
            }
        }
        return rects;
    }

    // Pure geometry helpers, kept side-effect-free and exposed separately so they're testable
    // without a DOM -- mirrors the window.hoshiRubyGeometry convention used elsewhere in this
    // reader (see shared/selection.js).
    // Where a sentence sits in a block's lines, whitespace ignored: one {line, start, end} per line
    // it touches (UTF-16 offsets into that line, end exclusive); [] when the block lacks it.
    function sentenceLineRanges(lines, sentence) {
        var target = String(sentence || '').replace(/\s+/g, '');
        if (!target) return [];
        var joined = '';
        var chars = [];
        for (var l = 0; l < lines.length; l++) {
            var text = String(lines[l] || '');
            for (var i = 0; i < text.length; i++) {
                if (/\s/.test(text[i])) continue;
                joined += text[i];
                chars.push({ line: l, index: i });
            }
        }
        var at = joined.indexOf(target);
        if (at < 0) return [];
        var ranges = [];
        for (var k = at; k < at + target.length; k++) {
            var c = chars[k];
            var last = ranges[ranges.length - 1];
            if (last && last.line === c.line) {
                last.end = c.index + 1;
            } else {
                ranges.push({ line: c.line, start: c.index, end: c.index + 1 });
            }
        }
        return ranges;
    }

    // A client rect inside a block, in page image pixels: the block's own client rect and its page
    // box give the mapping, so zoom and scrolling cancel out.
    function clientRectToPageBox(rect, blockRect, box) {
        var sx = (box[2] - box[0]) / blockRect.width;
        var sy = (box[3] - box[1]) / blockRect.height;
        return [
            box[0] + (rect.left - blockRect.left) * sx,
            box[1] + (rect.top - blockRect.top) * sy,
            box[0] + (rect.right - blockRect.left) * sx,
            box[1] + (rect.bottom - blockRect.top) * sy,
        ];
    }

    window.hoshiMangaGeometry = {
        sentenceLineRanges: sentenceLineRanges,
        clientRectToPageBox: clientRectToPageBox,
        fitDisplaySize: fitDisplaySize,
        fitSpreadDisplaySize: fitSpreadDisplaySize,
        normalizedCrop: normalizedCrop,
        croppedPageSize: croppedPageSize,
        pageCropLayout: pageCropLayout,
        visibleRect: visibleRect,
        axisAlignedBoxFromPoints: axisAlignedBoxFromPoints,
        lineRects: lineRects,
        blockFontSizePx: blockFontSizePx,
        fittedFontSizePx: fittedFontSizePx,
        glyphCount: glyphCount,
        longestLineGlyphCount: longestLineGlyphCount,
        rectDistance: rectDistance,
        nearestRectIndex: nearestRectIndex,
        nearestRectCenter: nearestRectCenter,
    };

    // Kept pure (blocks in, action out) for the same reason as hoshiMangaGeometry.
    window.hoshiMangaTap = {
        blockTapAction: blockTapAction,
    };

    var state = {
        rtl: true,
        eInkMode: false,
        scale: 1,
        translateX: 0,
        translateY: 0,
        pageWidth: 0,
        pageHeight: 0,
    };

    var MAX_SLOTS = 2;
    var viewportEl = null;
    var pageEl = null;
    var slots = []; // { el, imageEl, overlayEl }, one per potential spread slot
    // The one OCR block currently revealed as readable text (see selectTextAt), or null.
    var activeBlockEl = null;

    function ensureDom() {
        if (viewportEl) return;
        viewportEl = document.createElement('div');
        viewportEl.className = 'hoshi-manga-viewport';

        pageEl = document.createElement('div');
        pageEl.className = 'hoshi-manga-page';

        for (var i = 0; i < MAX_SLOTS; i++) {
            var slotEl = document.createElement('div');
            slotEl.className = 'hoshi-manga-slot';

            var imageEl = document.createElement('img');
            imageEl.className = 'hoshi-manga-page-image';
            imageEl.draggable = false;

            var overlayEl = document.createElement('div');
            overlayEl.className = 'hoshi-manga-overlay';

            slotEl.appendChild(imageEl);
            slotEl.appendChild(overlayEl);
            pageEl.appendChild(slotEl);
            slots.push({ el: slotEl, imageEl: imageEl, overlayEl: overlayEl });
        }

        viewportEl.appendChild(pageEl);
        document.body.appendChild(viewportEl);
    }

    // Number of code points, so a surrogate pair counts as the one full-width glyph it renders as.
    function glyphCount(line) {
        var count = 0;
        for (var i = 0; i < line.length; i++) {
            var code = line.charCodeAt(i);
            if (code >= 0xd800 && code <= 0xdbff && i + 1 < line.length) i++;
            count++;
        }
        return count;
    }

    function longestLineGlyphCount(lines) {
        var longest = 0;
        for (var i = 0; i < lines.length; i++) {
            var count = glyphCount(lines[i]);
            if (count > longest) longest = count;
        }
        return longest;
    }

    // Japanese text is full-width: every glyph advances exactly one em, so a run of `glyphs`
    // characters spanning a known extent pins the font size at `longExtent / glyphs` with no
    // measurement at all.
    //
    // This is what makes a synthesized run end where the printed run ends. mokuro's reported
    // `font_size` is a per-block average of OCR'd glyph heights measured in a typeface we do not
    // render in, and the previous per-line estimate used the column's *width* scaled by a
    // constant -- the axis the text does not run along -- so neither had any way to make an
    // n-character run come out n characters long. Both produced a run that started in the right
    // place and finished short, leaving the last character or two of every line outside the box
    // and therefore untappable.
    //
    // `crossExtent` caps the result, since the glyphs also have to fit across the line; that stops
    // one malformed or merged OCR box from rendering enormous text. Returns 0 when the block's
    // geometry cannot produce a size, so callers can fall back.
    function fittedFontSizePx(longExtent, crossExtent, glyphs) {
        if (!(glyphs > 0) || !(longExtent > 0)) return 0;
        var alongRun = longExtent / glyphs;
        var size = crossExtent > 0 ? Math.min(alongRun, crossExtent) : alongRun;
        return Math.max(6, size);
    }

    // Fallback only, for a block whose own geometry cannot produce a size (see fittedFontSizePx).
    function blockFontSizePx(block, displayScale) {
        if (typeof block.font_size === 'number' && block.font_size > 0) {
            return Math.max(6, block.font_size * displayScale);
        }
        // font_size missing from the OCR data (rare, but the schema allows it) -- approximate
        // from the block's own box divided by line count, matching the old per-block estimate.
        var box = block.box;
        var extent = block.vertical ? (box[2] - box[0]) : (box[3] - box[1]);
        var n = Math.max(1, block.lines.length);
        return Math.max(6, (extent / n) * displayScale * 0.9);
    }

    // Both branches below size the block <p> to the OCR box itself. That box -- unlike the
    // synthesized glyph positions inside it -- is real data straight from the OCR, so it is the
    // only part of the overlay guaranteed to line up with the printed art. It therefore serves
    // as both the tap target that reveals a block and the opaque card drawn once it is revealed
    // (see selectTextAt).
    function buildOverlay(overlayEl, pageJson, displayScale) {
        overlayEl.textContent = '';
        var blocks = pageJson.blocks || [];
        for (var b = 0; b < blocks.length; b++) {
            var block = blocks[b];
            if (!block.box || block.box.length !== 4 || !block.lines || !block.lines.length) continue;
            var writingMode = block.vertical ? 'vertical-rl' : 'horizontal-tb';
            var hasPreciseCoords = block.lines_coords && block.lines_coords.length === block.lines.length;
            var box = block.box;
            var boxLeft = box[0] * displayScale;
            var boxTop = box[1] * displayScale;
            var boxWidth = Math.max(1, (box[2] - box[0]) * displayScale);
            var boxHeight = Math.max(1, (box[3] - box[1]) * displayScale);

            var blockEl = document.createElement('p');
            // Kept a <p> either way -- hoshiSelection.findParagraph() (shared/selection.js) scopes
            // sentence-context lookups to the nearest `p, .glossary-content, .expr-tag` ancestor,
            // so this tag is load-bearing regardless of which positioning strategy is used below.
            blockEl.className = 'hoshi-manga-block';
            // Where the bubble is in the original page image, for card screenshots (activeBlockImage).
            blockEl.dataset.imgPath = pageJson.img_path || '';
            blockEl.dataset.box = box.join(',');
            blockEl.dataset.imgWidth = String(pageJson.img_width || 0);
            blockEl.dataset.imgHeight = String(pageJson.img_height || 0);
            blockEl.style.writingMode = writingMode;
            blockEl.style.left = boxLeft + 'px';
            blockEl.style.top = boxTop + 'px';
            blockEl.style.width = boxWidth + 'px';
            blockEl.style.height = boxHeight + 'px';

            if (hasPreciseCoords) {
                // Real per-line polygon data available -- keep exact per-line absolute
                // positioning, already accurate since it's not a guess. Line offsets are relative
                // to the block's own box now that the block carries `left`/`top` of its own (it
                // previously collapsed to a zero-sized element pinned at the overlay origin, so
                // page-space line coordinates happened to work; they would double-count today).
                var rects = lineRects(block);
                for (var i = 0; i < block.lines.length; i++) {
                    var rect = rects[i];
                    if (!rect) continue;
                    var width = Math.max(1, (rect.x2 - rect.x1) * displayScale);
                    var height = Math.max(1, (rect.y2 - rect.y1) * displayScale);
                    var lineEl = document.createElement('span');
                    lineEl.className = 'hoshi-manga-line';
                    lineEl.style.left = (rect.x1 * displayScale - boxLeft) + 'px';
                    lineEl.style.top = (rect.y1 * displayScale - boxTop) + 'px';
                    lineEl.style.width = width + 'px';
                    lineEl.style.height = height + 'px';
                    lineEl.style.writingMode = writingMode;
                    // This line's own polygon is real OCR data, so its long axis is exactly the
                    // extent the printed run occupies -- dividing that by this line's own glyph
                    // count lands every character on its printed counterpart.
                    var fontSizePx = fittedFontSizePx(
                        block.vertical ? height : width,
                        block.vertical ? width : height,
                        glyphCount(block.lines[i]),
                    ) || (block.vertical ? width * 0.92 : height * 0.78);
                    lineEl.style.fontSize = Math.max(6, fontSizePx) + 'px';
                    lineEl.textContent = block.lines[i];
                    blockEl.appendChild(lineEl);
                }
            } else {
                // No precise per-line coordinates (the common case for real-world mokuro output)
                // -- size the container to the whole block and let normal document flow place
                // each line: in a vertical-rl container, block-level children automatically stack
                // as right-to-left columns using their own real rendered width, matching mokuro's
                // own approach and manga's own reading order, with no subdivision guess involved.
                blockEl.classList.add('hoshi-manga-block-flow');
                // No per-line data here, so the box's long axis belongs to whichever line is
                // longest, and its cross axis has to hold one column per line.
                blockEl.style.fontSize = (
                    fittedFontSizePx(
                        block.vertical ? boxHeight : boxWidth,
                        (block.vertical ? boxWidth : boxHeight) / Math.max(1, block.lines.length),
                        longestLineGlyphCount(block.lines),
                    ) || blockFontSizePx(block, displayScale)
                ) + 'px';
                for (var j = 0; j < block.lines.length; j++) {
                    var flowLineEl = document.createElement('span');
                    flowLineEl.className = 'hoshi-manga-line hoshi-manga-line-flow';
                    flowLineEl.textContent = block.lines[j];
                    blockEl.appendChild(flowLineEl);
                }
            }
            overlayEl.appendChild(blockEl);
        }
    }

    // The overlay's glyphs are synthesized, so they can never sit exactly on the printed ones.
    // Rather than chase that, taps are made tolerant: a tap that misses every glyph but lands in
    // (or just outside) an OCR block is retried against the visually nearest character in that
    // block. The tolerance is in viewport CSS pixels, so it stays a constant physical slop
    // regardless of how far the page is zoomed in -- it models the finger, not the artwork.
    var SNAP_TOLERANCE_PX = 24;

    function rectDistance(rect, x, y) {
        var dx = Math.max(rect.left - x, 0, x - rect.right);
        var dy = Math.max(rect.top - y, 0, y - rect.bottom);
        return Math.sqrt(dx * dx + dy * dy);
    }

    // Index of the rect nearest (x, y), or -1 when even the nearest is further away than
    // `tolerance`. Kept pure (rects in, index out) so the snapping decision is testable without a
    // DOM; the callers below supply the rects.
    function nearestRectIndex(rects, x, y, tolerance) {
        var best = -1;
        var bestDistance = Infinity;
        for (var i = 0; i < rects.length; i++) {
            var distance = rectDistance(rects[i], x, y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
            if (bestDistance === 0) break;
        }
        return bestDistance <= tolerance ? best : -1;
    }

    // Centre of the rect nearest (x, y) -- or null when (x, y) already lies *inside* one of them,
    // which tells the caller the original hit test was accurate and that a miss therefore means
    // something other than bad aim (see selectTextAt). Zero-area rects are skipped: a collapsed
    // range (a line break, an empty text node) has no glyph to aim at.
    function nearestRectCenter(rects, x, y) {
        var best = null;
        var bestDistance = Infinity;
        for (var i = 0; i < rects.length; i++) {
            var rect = rects[i];
            if (!rect.width || !rect.height) continue;
            var distance = rectDistance(rect, x, y);
            if (distance === 0) return null;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
            }
        }
        return best;
    }

    // The part of `rect` inside `clip`, or a rect infinitely far away when none of it is.
    function visibleRect(rect, clip) {
        var left = Math.max(rect.left, clip.left);
        var top = Math.max(rect.top, clip.top);
        var right = Math.min(rect.right, clip.right);
        var bottom = Math.min(rect.bottom, clip.bottom);
        if (right <= left || bottom <= top) {
            return { left: Infinity, top: Infinity, right: Infinity, bottom: Infinity };
        }
        return { left: left, top: top, right: right, bottom: bottom };
    }

    function nearestBlockElement(x, y, tolerance) {
        // The revealed block wins any tie with a neighbour whose box overlaps it: it is the one
        // the reader can see, so it is the one they are aiming at.
        if (activeBlockEl && activeBlockEl.isConnected &&
            rectDistance(activeBlockEl.getBoundingClientRect(), x, y) === 0) {
            return activeBlockEl;
        }
        var els = document.querySelectorAll('.hoshi-manga-block');
        var rects = [];
        for (var i = 0; i < els.length; i++) {
            // A cropped-away block is clipped out of sight by its slot, so it must not win a snap
            // either; one straddling the crop edge competes with only its visible part.
            var slotEl = els[i].closest('.hoshi-manga-slot');
            var rect = els[i].getBoundingClientRect();
            rects.push(slotEl ? visibleRect(rect, slotEl.getBoundingClientRect()) : rect);
        }
        var index = nearestRectIndex(rects, x, y, tolerance);
        return index < 0 ? null : els[index];
    }

    // Mokuro-style tap handling. The page shows clean artwork; the synthesized glyphs cannot line
    // up with the printed ones, so a lookup straight off the invisible overlay picks a neighbouring
    // character too often. Instead, a first tap on a block only reveals it as readable text, and
    // lookups happen on that revealed text, where what the reader aims at is what gets hit.
    //
    //   'reveal'  -- single tap on a block that is not revealed: reveal it, look nothing up.
    //   'select'  -- tap on the revealed block, or a double-tap (sentence) on any block: look up.
    //                A sentence lookup is not precision-sensitive, so it needs no reveal first.
    //   'dismiss' -- single tap clear of every block while one is revealed: just hide it.
    //   'blank'   -- anything else clear of every block: the caller's blank-tap behaviour
    //                (zoom reset / page turn / chrome), so double-tap page turns always work.
    function blockTapAction(hitBlock, activeBlock, sentenceOnly) {
        if (!hitBlock) return activeBlock && !sentenceOnly ? 'dismiss' : 'blank';
        if (hitBlock === activeBlock || sentenceOnly) return 'select';
        return 'reveal';
    }

    function setActiveBlock(blockEl) {
        if (activeBlockEl === blockEl) return;
        if (activeBlockEl) activeBlockEl.classList.remove('hoshi-manga-block-active');
        activeBlockEl = blockEl;
        if (blockEl) blockEl.classList.add('hoshi-manga-block-active');
    }

    function characterRects(blockEl) {
        var walker = document.createTreeWalker(blockEl, NodeFilter.SHOW_TEXT, null);
        var range = document.createRange();
        var collected = [];
        var node;
        while ((node = walker.nextNode())) {
            var text = node.textContent;
            for (var i = 0; i < text.length;) {
                // Step by code point so a surrogate pair is never split across two probes.
                var code = text.charCodeAt(i);
                var end = (code >= 0xd800 && code <= 0xdbff && i + 1 < text.length) ? i + 2 : i + 1;
                range.setStart(node, i);
                range.setEnd(node, end);
                var rects = range.getClientRects();
                for (var r = 0; r < rects.length; r++) {
                    collected.push(rects[r]);
                }
                i = end;
            }
        }
        return collected;
    }

    function applyTransform(settling) {
        pageEl.classList.toggle('hoshi-manga-page-settling', !!settling);
        pageEl.style.transform =
            'translate(' + state.translateX + 'px, ' + state.translateY + 'px) scale(' + state.scale + ')';
    }

    window.hoshiManga = {
        // The revealed OCR block's page image and box in that image's pixels, or null.
        // highlights: the drawn glyphs of [sentence] in page image pixels, for the card's marker.
        activeBlockImage: function (sentence) {
            if (!activeBlockEl || !activeBlockEl.dataset.imgPath) return null;
            var box = activeBlockEl.dataset.box.split(',').map(Number);
            if (box.length !== 4 || box.some(isNaN)) return null;
            var highlights = [];
            var blockRect = activeBlockEl.getBoundingClientRect();
            if (sentence && blockRect.width > 0 && blockRect.height > 0) {
                var lineEls = activeBlockEl.querySelectorAll('.hoshi-manga-line');
                var lines = Array.prototype.map.call(lineEls, function (lineEl) { return lineEl.textContent; });
                sentenceLineRanges(lines, sentence).forEach(function (range) {
                    var node = lineEls[range.line].firstChild;
                    if (!node) return;
                    var domRange = document.createRange();
                    domRange.setStart(node, range.start);
                    domRange.setEnd(node, range.end);
                    Array.prototype.forEach.call(domRange.getClientRects(), function (rect) {
                        if (rect.width > 0 && rect.height > 0) highlights.push(clientRectToPageBox(rect, blockRect, box));
                    });
                });
            }
            return {
                imgPath: activeBlockEl.dataset.imgPath,
                box: box,
                imgWidth: Number(activeBlockEl.dataset.imgWidth),
                imgHeight: Number(activeBlockEl.dataset.imgHeight),
                highlights: highlights,
            };
        },

        configure: function (options) {
            options = options || {};
            state.rtl = options.rtl !== false;
            state.eInkMode = !!options.eInkMode;
            document.documentElement.dataset.hoshiReaderEinkMode = state.eInkMode ? 'true' : 'false';
        },

        // pagesJson/imageUrls are arrays of 1 (single page) or 2 (spread) entries, already in
        // final left-to-right *visual* order -- the caller (Kotlin) resolves RTL reading-order
        // into display order once, so this stays a plain "lay these out left to right" renderer
        // with no reading-direction logic of its own. `crops` (optional, same order) hides page
        // margins: only the cropped part of each page is fitted to the screen.
        showPage: function (pageIndex, pagesJson, imageUrls, crops) {
            ensureDom();
            var pages = Array.isArray(pagesJson) ? pagesJson : [pagesJson];
            var urls = Array.isArray(imageUrls) ? imageUrls : [imageUrls];
            var n = Math.min(pages.length, MAX_SLOTS);
            var pageCrops = pages.slice(0, n).map(function (p, i) {
                return normalizedCrop(Array.isArray(crops) ? crops[i] : null);
            });
            var fit = fitSpreadDisplaySize(
                pages.slice(0, n).map(function (p, i) {
                    return croppedPageSize({ width: p.img_width, height: p.img_height }, pageCrops[i]);
                }),
                window.innerWidth,
                window.innerHeight,
            );

            // The zoom/pan reset below is instant (no transition), but swapping imageEl.src is
            // asynchronous -- decode takes a beat. Committing both together right away meant the
            // reset painted immediately against the *old*, still-zoomed page (a visible snap back
            // to centered/unzoomed), with the new page only popping in a moment later once it
            // finished decoding -- the "zooms out, then goes to next page" two-step. Preloading
            // off-DOM and committing everything (transform reset + overlay rebuild + visible
            // image swap) only once every page in the (1- or 2-page) spread is actually ready
            // makes it one clean change instead of a flash per slot.
            var preloads = urls.slice(0, n).map(function (url) {
                var img = new Image();
                img.src = url;
                return img;
            });
            var commit = function () {
                // Its element is about to be discarded by buildOverlay.
                activeBlockEl = null;
                var offsetX = 0;
                for (var i = 0; i < n; i++) {
                    var slot = slots[i];
                    var layout = pageCropLayout(
                        { width: pages[i].img_width, height: pages[i].img_height },
                        pageCrops[i],
                        fit.scales[i],
                    );
                    slot.el.style.display = '';
                    slot.el.style.left = offsetX + 'px';
                    slot.el.style.width = layout.slotWidth + 'px';
                    slot.el.style.height = layout.slotHeight + 'px';
                    [slot.imageEl, slot.overlayEl].forEach(function (el) {
                        el.style.left = layout.imageLeft + 'px';
                        el.style.top = layout.imageTop + 'px';
                        el.style.width = layout.imageWidth + 'px';
                        el.style.height = layout.imageHeight + 'px';
                    });
                    slot.imageEl.src = urls[i];
                    buildOverlay(slot.overlayEl, pages[i], fit.scales[i]);
                    offsetX += layout.slotWidth;
                }
                for (var j = n; j < slots.length; j++) {
                    slots[j].el.style.display = 'none';
                    slots[j].imageEl.removeAttribute('src');
                    slots[j].overlayEl.textContent = '';
                }
                state.pageWidth = fit.totalWidth;
                state.pageHeight = fit.totalHeight;
                state.scale = 1;
                state.lastScale = 1;
                state.translateX = (window.innerWidth - fit.totalWidth) / 2;
                state.translateY = (window.innerHeight - fit.totalHeight) / 2;
                applyTransform(false);
            };
            var remaining = preloads.length;
            var onOneReady = function () {
                remaining--;
                if (remaining <= 0) commit();
            };
            if (remaining === 0) {
                commit();
                return;
            }
            preloads.forEach(function (img) {
                if (img.decode) {
                    img.decode().then(onOneReady, onOneReady);
                } else {
                    img.onload = onOneReady;
                    img.onerror = onOneReady;
                }
            });
        },

        // panDx/panDy carry the gesture's own focal-point movement between calls (two fingers
        // dragging together, not just spreading/pinching) -- applied as a plain translate before
        // the scale-anchor step below, so a pinch that also drifts, or a pure two-finger pan with
        // no distance change at all, both compose correctly into one transform.
        setZoom: function (scale, focalX, focalY, panDx, panDy, settling) {
            ensureDom();
            var clamped = Math.min(6, Math.max(1, scale));
            state.translateX += panDx || 0;
            state.translateY += panDy || 0;
            // Keep the point under the gesture's focal point stationary while scaling.
            state.translateX = focalX - (focalX - state.translateX) * (clamped / (state.lastScale || 1));
            state.translateY = focalY - (focalY - state.translateY) * (clamped / (state.lastScale || 1));
            state.scale = clamped;
            state.lastScale = clamped;
            applyTransform(!!settling);
        },

        panBy: function (dx, dy, settling) {
            ensureDom();
            state.translateX += dx;
            state.translateY += dy;
            applyTransform(!!settling);
        },

        resetZoom: function (settling) {
            ensureDom();
            state.scale = 1;
            state.lastScale = 1;
            state.translateX = (window.innerWidth - state.pageWidth) / 2;
            state.translateY = (window.innerHeight - state.pageHeight) / 2;
            applyTransform(settling !== false);
        },

        isZoomed: function () {
            return state.scale > 1.001;
        },

        // The manga reader's entry point for taps, replacing a direct
        // hoshiSelection.selectText() call; see blockTapAction for when a tap reveals a block
        // versus looks text up. Return values, all consumed by Kotlin's MangaSelectionResult: the
        // selected text on success; 'image'/'link' passed through from hoshiSelection;
        // 'block-toggled' when the tap only revealed or hid a block; 'near-text' when the tap was
        // on the revealed block but produced no new selection (a deliberate tap-again-to-dismiss,
        // or glyphs that could not be resolved); and null only for a blank tap. Everything but
        // null must NOT be treated as tapping blank art.
        selectTextAt: function (x, y, maxLength, sentenceOnly) {
            var selection = window.hoshiSelection;
            if (!selection) return null;
            var blockEl = nearestBlockElement(x, y, SNAP_TOLERANCE_PX);
            var action = blockTapAction(blockEl, activeBlockEl, !!sentenceOnly);
            if (action === 'blank') {
                setActiveBlock(null);
                return null;
            }
            if (action === 'dismiss') {
                setActiveBlock(null);
                return 'block-toggled';
            }
            setActiveBlock(blockEl);
            if (action === 'reveal') return 'block-toggled';

            var direct = selection.selectText(x, y, maxLength, x, y, !!sentenceOnly);
            if (direct !== null && direct !== undefined) return direct;
            var point = nearestRectCenter(characterRects(blockEl), x, y);
            if (point) {
                var snapped = selection.selectText(point.x, point.y, maxLength, point.x, point.y, !!sentenceOnly);
                if (snapped !== null && snapped !== undefined) return snapped;
            }
            return 'near-text';
        },
    };
})();
