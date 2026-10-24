// Watch mode subtitle overlay: draws the current Japanese lines over the video and routes taps
// into the shared hoshiSelection engine.
//
// Each cue is its own <p>: hoshiSelection.findParagraph() scopes sentence context to the nearest
// <p>, so a lookup never reads across two subtitle lines.
(function() {
    if (window.hoshiWatch) return;

    var rootEl = null;
    var shownKey = null;

    function ensureDom() {
        if (rootEl) return;
        rootEl = document.createElement('div');
        rootEl.className = 'hoshi-watch-subtitles';
        document.body.appendChild(rootEl);
    }

    window.hoshiWatch = {
        configure: function(options) {
            ensureDom();
            var style = document.documentElement.style;
            if (options.fontSizePx) style.setProperty('--hoshi-watch-font-size', options.fontSizePx + 'px');
        },

        // Distance between the bottom of the screen and the lowest line, so the lines move up
        // while the player controls are visible.
        setBottomInset: function(px) {
            document.documentElement.style.setProperty('--hoshi-watch-bottom-inset', px + 'px');
        },

        // lines: texts of the Japanese cues shown now.
        showCues: function(lines) {
            ensureDom();
            var key = JSON.stringify(lines);
            if (key === shownKey) return;
            shownKey = key;
            if (window.hoshiSelection) window.hoshiSelection.clearSelection();
            rootEl.textContent = '';
            for (var i = 0; i < lines.length; i++) {
                var lineEl = document.createElement('p');
                lineEl.className = 'hoshi-watch-line';
                lineEl.textContent = lines[i];
                rootEl.appendChild(lineEl);
            }
        },

        // Returns the selected text, or null when the tap was not on a Japanese line.
        selectTextAt: function(x, y, maxLength, sentenceOnly) {
            if (!window.hoshiSelection) return null;
            var hit = document.elementFromPoint(x, y);
            if (!hit || !hit.closest('.hoshi-watch-line')) {
                window.hoshiSelection.clearSelection();
                return null;
            }
            return window.hoshiSelection.selectText(x, y, maxLength, x, y, sentenceOnly);
        }
    };
})();
