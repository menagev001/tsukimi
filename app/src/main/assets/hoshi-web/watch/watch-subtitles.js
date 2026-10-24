// Watch mode subtitle overlay: draws the current Japanese lines over the video and routes taps
// into the shared hoshiSelection engine.
//
// Each cue is its own <p>: hoshiSelection.findParagraph() scopes sentence context to the nearest
// <p>, so a lookup never reads across two subtitle lines. The same holds for the line list panel.
//
// Kotlin interprets all touches; selectTextAt returns a token for taps that are not lookups:
// 'seek:<cue index>' for a line's timestamp in the list, 'list' for other taps in the list.
(function() {
    if (window.hoshiWatch) return;

    // After the user scrolls the list, it stops following the playing line for this long.
    var FOLLOW_PAUSE_MS = 4000;

    var rootEl = null;
    var listEl = null;
    var listItems = [];
    var activeIndex = -1;
    var lastUserScroll = 0;
    var shownKey = null;

    function ensureDom() {
        if (rootEl) return;
        rootEl = document.createElement('div');
        rootEl.className = 'hoshi-watch-subtitles';
        document.body.appendChild(rootEl);
        listEl = document.createElement('div');
        listEl.className = 'hoshi-watch-list';
        document.body.appendChild(listEl);
    }

    function formatTime(ms) {
        var total = Math.floor(ms / 1000);
        var hours = Math.floor(total / 3600);
        var minutes = Math.floor((total % 3600) / 60);
        var seconds = total % 60;
        var pad = function(value) { return value < 10 ? '0' + value : String(value); };
        return hours > 0 ? hours + ':' + pad(minutes) + ':' + pad(seconds) : minutes + ':' + pad(seconds);
    }

    function listIsOpen() {
        return document.documentElement.classList.contains('hoshi-watch-list-open');
    }

    function scrollToActive(smooth) {
        var item = listItems[activeIndex];
        if (!item || !listIsOpen()) return;
        var top = item.offsetTop - listEl.clientHeight / 3;
        if (listEl.scrollTo) {
            listEl.scrollTo({ top: Math.max(0, top), behavior: smooth ? 'smooth' : 'auto' });
        } else {
            listEl.scrollTop = Math.max(0, top);
        }
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

        // cues: [{ start: ms, text }] of the whole track, for the list panel.
        setCueList: function(cues) {
            ensureDom();
            listEl.textContent = '';
            listItems = [];
            activeIndex = -1;
            for (var i = 0; i < cues.length; i++) {
                var itemEl = document.createElement('div');
                itemEl.className = 'hoshi-watch-list-item';
                var timeEl = document.createElement('span');
                timeEl.className = 'hoshi-watch-list-time';
                timeEl.dataset.cueIndex = String(i);
                timeEl.textContent = formatTime(cues[i].start);
                var lineEl = document.createElement('p');
                lineEl.className = 'hoshi-watch-list-line';
                lineEl.textContent = cues[i].text;
                itemEl.appendChild(timeEl);
                itemEl.appendChild(lineEl);
                listEl.appendChild(itemEl);
                listItems.push(itemEl);
            }
        },

        setActiveCue: function(index) {
            ensureDom();
            if (index === activeIndex) return;
            if (listItems[activeIndex]) listItems[activeIndex].classList.remove('hoshi-watch-list-active');
            activeIndex = index;
            if (listItems[activeIndex]) listItems[activeIndex].classList.add('hoshi-watch-list-active');
            if (Date.now() - lastUserScroll > FOLLOW_PAUSE_MS) scrollToActive(true);
        },

        showList: function(visible) {
            ensureDom();
            document.documentElement.classList.toggle('hoshi-watch-list-open', visible);
            if (visible) scrollToActive(false);
        },

        setListWidth: function(px) {
            document.documentElement.style.setProperty('--hoshi-watch-list-width', px + 'px');
        },

        scrollList: function(dy) {
            if (!listEl) return;
            lastUserScroll = Date.now();
            listEl.scrollTop += dy;
        },

        flingList: function(dy) {
            if (!listEl) return;
            lastUserScroll = Date.now();
            if (listEl.scrollBy) {
                listEl.scrollBy({ top: dy, behavior: 'smooth' });
            } else {
                listEl.scrollTop += dy;
            }
        },

        // Returns the selected text, a 'seek:'/'list' token (see above), or null when the tap hit
        // neither a Japanese line nor the list.
        selectTextAt: function(x, y, maxLength, sentenceOnly) {
            if (!window.hoshiSelection) return null;
            var hit = document.elementFromPoint(x, y);
            var time = hit && hit.closest('.hoshi-watch-list-time');
            if (time) {
                window.hoshiSelection.clearSelection();
                return 'seek:' + time.dataset.cueIndex;
            }
            var inList = !!(hit && hit.closest('.hoshi-watch-list'));
            if (!hit || !hit.closest('.hoshi-watch-line, .hoshi-watch-list-line')) {
                window.hoshiSelection.clearSelection();
                return inList ? 'list' : null;
            }
            var text = window.hoshiSelection.selectText(x, y, maxLength, x, y, sentenceOnly);
            return text || (inList ? 'list' : null);
        }
    };
})();
