import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../../main/assets/hoshi-web/watch/watch-subtitles.js', import.meta.url), 'utf8');

function fakeElement(tagName) {
    return {
        tagName,
        className: '',
        children: [],
        _text: '',
        set textContent(value) {
            this._text = value;
            this.children = [];
        },
        get textContent() {
            return this._text;
        },
        appendChild(child) {
            this.children.push(child);
            child.parentNode = this;
            return child;
        },
        closest(selector) {
            let node = this;
            const className = selector.replace('.', '');
            while (node) {
                if (node.className === className) return node;
                node = node.parentNode;
            }
            return null;
        },
    };
}

function watchContext({ hitAt = () => null } = {}) {
    const styleProperties = {};
    const selectCalls = [];
    let clearCount = 0;
    const body = fakeElement('body');
    const document = {
        body,
        documentElement: {
            style: {
                setProperty(name, value) {
                    styleProperties[name] = value;
                },
            },
        },
        createElement: fakeElement,
        elementFromPoint: (x, y) => hitAt(x, y, body),
    };
    const window = {
        hoshiSelection: {
            clearSelection() {
                clearCount += 1;
            },
            selectText(...args) {
                selectCalls.push(args);
                return '言葉';
            },
        },
    };
    const context = vm.createContext({ window, document, JSON });
    vm.runInContext(source, context);
    return {
        hoshiWatch: window.hoshiWatch,
        body,
        styleProperties,
        selectCalls,
        get clearCount() {
            return clearCount;
        },
    };
}

test('showCues draws one paragraph per cue and skips redraws of the same cues', () => {
    const watch = watchContext();
    watch.hoshiWatch.showCues(['一行目', '二行目']);

    const [root] = watch.body.children;
    assert.deepEqual(root.children.map((line) => line.textContent), ['一行目', '二行目']);
    assert.deepEqual(root.children.map((line) => line.tagName), ['p', 'p']);

    const clearsBefore = watch.clearCount;
    watch.hoshiWatch.showCues(['一行目', '二行目']);
    assert.equal(watch.clearCount, clearsBefore);

    watch.hoshiWatch.showCues([]);
    assert.equal(root.children.length, 0);
    assert.equal(watch.clearCount, clearsBefore + 1);
});

test('selectTextAt only selects on a Japanese line', () => {
    let hitLine = false;
    const watch = watchContext({
        hitAt: (_x, _y, body) => (hitLine ? body.children[0].children[0] : body),
    });
    watch.hoshiWatch.showCues(['こんにちは']);

    assert.equal(watch.hoshiWatch.selectTextAt(10, 20, 16, false), null);
    assert.equal(watch.selectCalls.length, 0);

    hitLine = true;
    assert.equal(watch.hoshiWatch.selectTextAt(10, 20, 16, true), '言葉');
    assert.deepEqual(watch.selectCalls, [[10, 20, 16, 10, 20, true]]);
});

test('configure and setBottomInset set CSS variables', () => {
    const watch = watchContext();
    watch.hoshiWatch.configure({ fontSizePx: 40 });
    watch.hoshiWatch.setBottomInset(120);

    assert.equal(watch.styleProperties['--hoshi-watch-font-size'], '40px');
    assert.equal(watch.styleProperties['--hoshi-watch-bottom-inset'], '120px');
});
