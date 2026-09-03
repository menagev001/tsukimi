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
    window.hoshiMangaGeometry = {
        fitDisplaySize: fitDisplaySize,
        fitSpreadDisplaySize: fitSpreadDisplaySize,
        axisAlignedBoxFromPoints: axisAlignedBoxFromPoints,
        lineRects: lineRects,
        blockFontSizePx: blockFontSizePx,
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

    // mokuro's own reference reader (mokuro/legacy/overlay_generator.py + styles.css) doesn't try
    // to guess individual line positions when precise per-line coordinates aren't available --
    // it sizes one container to the whole block's bounding box and lets the *browser's own text
    // layout* place each line, using the real rendered glyph widths. Evenly subdividing the block
    // into N equal columns/rows (what this used to do) assumes every line is the same length,
    // which real OCR'd dialogue never is -- a short line and a long line sharing one block get
    // the same subdivided width, so the short line's synthesized box sits offset from where its
    // glyphs actually are. That mismatch is the direct cause of taps needing to land "slightly
    // off" from the visible word. Font size similarly prefers the OCR's own reported font_size
    // (mokuro clips this the same way) over a guess derived from a possibly-wrong subdivided rect.
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

    function buildOverlay(overlayEl, pageJson, displayScale) {
        overlayEl.textContent = '';
        var blocks = pageJson.blocks || [];
        for (var b = 0; b < blocks.length; b++) {
            var block = blocks[b];
            if (!block.box || block.box.length !== 4 || !block.lines || !block.lines.length) continue;
            var writingMode = block.vertical ? 'vertical-rl' : 'horizontal-tb';
            var hasPreciseCoords = block.lines_coords && block.lines_coords.length === block.lines.length;

            var blockEl = document.createElement('p');
            // Kept a <p> either way -- hoshiSelection.findParagraph() (shared/selection.js) scopes
            // sentence-context lookups to the nearest `p, .glossary-content, .expr-tag` ancestor,
            // so this tag is load-bearing regardless of which positioning strategy is used below.
            blockEl.className = 'hoshi-manga-block';
            blockEl.style.writingMode = writingMode;

            if (hasPreciseCoords) {
                // Real per-line polygon data available -- keep exact per-line absolute
                // positioning, already accurate since it's not a guess.
                var rects = lineRects(block);
                for (var i = 0; i < block.lines.length; i++) {
                    var rect = rects[i];
                    if (!rect) continue;
                    var width = Math.max(1, (rect.x2 - rect.x1) * displayScale);
                    var height = Math.max(1, (rect.y2 - rect.y1) * displayScale);
                    var lineEl = document.createElement('span');
                    lineEl.className = 'hoshi-manga-line';
                    lineEl.style.left = (rect.x1 * displayScale) + 'px';
                    lineEl.style.top = (rect.y1 * displayScale) + 'px';
                    lineEl.style.width = width + 'px';
                    lineEl.style.height = height + 'px';
                    lineEl.style.writingMode = writingMode;
                    // Text glyphs must actually cover the tap region (not just the span's box) so
                    // caretPositionFromPoint/elementFromPoint hit-test correctly anywhere inside
                    // it -- sizing relative to the precise rect's own narrow dimension fills it
                    // reasonably via the browser's own text layout, without per-character
                    // placement.
                    var fontSizePx = block.vertical ? width * 0.92 : height * 0.78;
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
                var box = block.box;
                blockEl.classList.add('hoshi-manga-block-flow');
                blockEl.style.left = (box[0] * displayScale) + 'px';
                blockEl.style.top = (box[1] * displayScale) + 'px';
                blockEl.style.width = Math.max(1, (box[2] - box[0]) * displayScale) + 'px';
                blockEl.style.height = Math.max(1, (box[3] - box[1]) * displayScale) + 'px';
                blockEl.style.fontSize = blockFontSizePx(block, displayScale) + 'px';
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

    function applyTransform(settling) {
        pageEl.classList.toggle('hoshi-manga-page-settling', !!settling);
        pageEl.style.transform =
            'translate(' + state.translateX + 'px, ' + state.translateY + 'px) scale(' + state.scale + ')';
    }

    window.hoshiManga = {
        configure: function (options) {
            options = options || {};
            state.rtl = options.rtl !== false;
            state.eInkMode = !!options.eInkMode;
            document.documentElement.dataset.hoshiReaderEinkMode = state.eInkMode ? 'true' : 'false';
        },

        // pagesJson/imageUrls are arrays of 1 (single page) or 2 (spread) entries, already in
        // final left-to-right *visual* order -- the caller (Kotlin) resolves RTL reading-order
        // into display order once, so this stays a plain "lay these out left to right" renderer
        // with no reading-direction logic of its own.
        showPage: function (pageIndex, pagesJson, imageUrls) {
            ensureDom();
            var pages = Array.isArray(pagesJson) ? pagesJson : [pagesJson];
            var urls = Array.isArray(imageUrls) ? imageUrls : [imageUrls];
            var n = Math.min(pages.length, MAX_SLOTS);
            var fit = fitSpreadDisplaySize(
                pages.slice(0, n).map(function (p) { return { width: p.img_width, height: p.img_height }; }),
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
                var offsetX = 0;
                for (var i = 0; i < n; i++) {
                    var slot = slots[i];
                    var width = fit.widths[i];
                    var height = fit.heights[i];
                    slot.el.style.display = '';
                    slot.el.style.left = offsetX + 'px';
                    slot.imageEl.style.width = width + 'px';
                    slot.imageEl.style.height = height + 'px';
                    slot.overlayEl.style.width = width + 'px';
                    slot.overlayEl.style.height = height + 'px';
                    slot.imageEl.src = urls[i];
                    buildOverlay(slot.overlayEl, pages[i], fit.scales[i]);
                    offsetX += width;
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
    };
})();
