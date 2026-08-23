(function () {
    'use strict';

    function fitDisplaySize(naturalWidth, naturalHeight, viewportWidth, viewportHeight) {
        var scale = Math.min(viewportWidth / naturalWidth, viewportHeight / naturalHeight);
        return {
            width: naturalWidth * scale,
            height: naturalHeight * scale,
            scale: scale,
        };
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
        axisAlignedBoxFromPoints: axisAlignedBoxFromPoints,
        lineRects: lineRects,
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

    var viewportEl = null;
    var pageEl = null;
    var imageEl = null;
    var overlayEl = null;

    function ensureDom() {
        if (viewportEl) return;
        viewportEl = document.createElement('div');
        viewportEl.className = 'hoshi-manga-viewport';

        pageEl = document.createElement('div');
        pageEl.className = 'hoshi-manga-page';

        imageEl = document.createElement('img');
        imageEl.className = 'hoshi-manga-page-image';
        imageEl.draggable = false;

        overlayEl = document.createElement('div');
        overlayEl.className = 'hoshi-manga-overlay';

        pageEl.appendChild(imageEl);
        pageEl.appendChild(overlayEl);
        viewportEl.appendChild(pageEl);
        document.body.appendChild(viewportEl);
    }

    function buildOverlay(pageJson, displayScale) {
        overlayEl.textContent = '';
        var blocks = pageJson.blocks || [];
        for (var b = 0; b < blocks.length; b++) {
            var block = blocks[b];
            if (!block.box || block.box.length !== 4 || !block.lines || !block.lines.length) continue;
            var blockEl = document.createElement('p');
            blockEl.className = 'hoshi-manga-block';
            var writingMode = block.vertical ? 'vertical-rl' : 'horizontal-tb';
            blockEl.style.writingMode = writingMode;

            var rects = lineRects(block);
            for (var i = 0; i < block.lines.length; i++) {
                var rect = rects[i];
                if (!rect) continue;
                var left = rect.x1 * displayScale;
                var top = rect.y1 * displayScale;
                var width = Math.max(1, (rect.x2 - rect.x1) * displayScale);
                var height = Math.max(1, (rect.y2 - rect.y1) * displayScale);
                var lineEl = document.createElement('span');
                lineEl.className = 'hoshi-manga-line';
                lineEl.style.left = left + 'px';
                lineEl.style.top = top + 'px';
                lineEl.style.width = width + 'px';
                lineEl.style.height = height + 'px';
                lineEl.style.writingMode = writingMode;
                // Text glyphs must actually cover the tap region (not just the span's box) so
                // caretPositionFromPoint/elementFromPoint hit-test correctly anywhere inside it --
                // sizing font relative to the narrow dimension of the column/row fills the box
                // reasonably via the browser's own text layout, without per-character placement.
                var fontSizePx = block.vertical ? width * 0.92 : height * 0.78;
                lineEl.style.fontSize = Math.max(6, fontSizePx) + 'px';
                lineEl.textContent = block.lines[i];
                blockEl.appendChild(lineEl);
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

        showPage: function (pageIndex, pageJson, imageUrl) {
            ensureDom();
            var fit = fitDisplaySize(pageJson.img_width, pageJson.img_height, window.innerWidth, window.innerHeight);
            state.pageWidth = fit.width;
            state.pageHeight = fit.height;
            state.scale = 1;
            state.translateX = 0;
            state.translateY = 0;
            imageEl.style.width = fit.width + 'px';
            imageEl.style.height = fit.height + 'px';
            overlayEl.style.width = fit.width + 'px';
            overlayEl.style.height = fit.height + 'px';
            imageEl.src = imageUrl;
            buildOverlay(pageJson, fit.scale);
            applyTransform(false);
        },

        setZoom: function (scale, focalX, focalY, settling) {
            ensureDom();
            var clamped = Math.min(6, Math.max(1, scale));
            state.scale = clamped;
            // Keep the point under the gesture's focal point stationary while scaling.
            state.translateX = focalX - (focalX - state.translateX) * (clamped / (state.lastScale || 1));
            state.translateY = focalY - (focalY - state.translateY) * (clamped / (state.lastScale || 1));
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
            state.translateX = 0;
            state.translateY = 0;
            applyTransform(settling !== false);
        },

        isZoomed: function () {
            return state.scale > 1.001;
        },
    };
})();
