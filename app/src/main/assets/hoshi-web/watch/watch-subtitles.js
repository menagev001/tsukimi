// Watch mode subtitle overlay: draws the current Japanese line (and an optional second line)
// over the video and routes taps into the shared hoshiSelection engine.
//
// Each cue is its own <p>: hoshiSelection.findParagraph() scopes sentence context to the nearest
// <p>, so a lookup never reads across two subtitle lines.
(function() {
    if (window.hoshiWatch) return;

    var rootEl = null;
    var primaryEl = null;
    var secondaryEl = null;
    var shownKey = null;

    function ensureDom() {
        if (rootEl) return;
        rootEl = document.createElement('div');
        rootEl.className = 'hoshi-watch-subtitles';
        primaryEl = document.createElement('div');
        primaryEl.className = 'hoshi-watch-primary';
        secondaryEl = document.createElement('div');
        secondaryEl.className = 'hoshi-watch-secondary';
        rootEl.appendChild(primaryEl);
        rootEl.appendChild(secondaryEl);
        document.body.appendChild(rootEl);
    }

    function fillLines(containerEl, lines, lineClass) {
        containerEl.textContent = '';
        for (var i = 0; i < lines.length; i++) {
            var lineEl = document.createElement('p');
            lineEl.className = lineClass;
            lineEl.textContent = lines[i];
            containerEl.appendChild(lineEl);
        }
    }

    window.hoshiWatch = {
        configure: function(options) {
            ensureDom();
            var style = document.documentElement.style;
            if (options.fontSizePx) style.setProperty('--hoshi-watch-font-size', options.fontSizePx + 'px');
            if (options.secondaryFontSizePx) {
                style.setProperty('--hoshi-watch-secondary-font-size', options.secondaryFontSizePx + 'px');
            }
        },

        // Distance between the bottom of the screen and the lowest line, so the lines move up
        // while the player controls are visible.
        setBottomInset: function(px) {
            document.documentElement.style.setProperty('--hoshi-watch-bottom-inset', px + 'px');
        },

        // primary: Japanese cue texts shown now; secondary: translation texts shown under them.
        showCues: function(primary, secondary) {
            ensureDom();
            var key = JSON.stringify([primary, secondary]);
            if (key === shownKey) return;
            shownKey = key;
            if (window.hoshiSelection) window.hoshiSelection.clearSelection();
            fillLines(primaryEl, primary, 'hoshi-watch-line');
            fillLines(secondaryEl, secondary, 'hoshi-watch-secondary-line');
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
