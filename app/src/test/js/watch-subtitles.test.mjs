import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../../main/assets/hoshi-web/watch/watch-subtitles.js', import.meta.url), 'utf8');

function fakeClassList(owner) {
    const classes = () => new Set(owner.className.split(' ').filter(Boolean));
    const write = (set) => {
        owner.className = [...set].join(' ');
    };
    return {
        add(name) {
            const set = classes();
            set.add(name);
            write(set);
        },
        remove(name) {
            const set = classes();
            set.delete(name);
            write(set);
        },
        contains: (name) => classes().has(name),
        toggle(name, force) {
            const set = classes();
            const on = force ?? !set.has(name);
            if (on) set.add(name); else set.delete(name);
            write(set);
            return on;
        },
    };
}

function fakeElement(tagName) {
    const element = {
        tagName,
        className: '',
        children: [],
        dataset: {},
        offsetTop: 0,
        scrollTop: 0,
        clientHeight: 300,
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
            const wanted = selector.split(',').map((part) => part.trim().replace('.', ''));
            let node = this;
            while (node) {
                const classes = node.className.split(' ');
                if (wanted.some((name) => classes.includes(name))) return node;
                node = node.parentNode;
            }
            return null;
        },
    };
    element.classList = fakeClassList(element);
    return element;
}

function watchContext({ hitAt = () => null } = {}) {
    const styleProperties = {};
    const selectCalls = [];
    let clearCount = 0;
    const body = fakeElement('body');
    const rootElement = { className: '' };
    rootElement.classList = fakeClassList(rootElement);
    const document = {
        body,
        documentElement: {
            classList: rootElement.classList,
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

test('line list renders every cue, marks the active one and maps taps to tokens', () => {
    let hit = null;
    const watch = watchContext({ hitAt: () => hit });
    watch.hoshiWatch.setCueList([
        { start: 1_000, text: '一つ目' },
        { start: 3_723_000, text: '二つ目' },
    ]);
    watch.hoshiWatch.showList(true);
    watch.hoshiWatch.setActiveCue(1);

    const list = watch.body.children[1];
    const [first, second] = list.children;
    assert.deepEqual(first.children.map((child) => child.textContent), ['0:01', '一つ目']);
    assert.equal(second.children[0].textContent, '1:02:03');
    assert.ok(second.className.includes('hoshi-watch-list-active'));
    assert.ok(!first.className.includes('hoshi-watch-list-active'));

    hit = second.children[0];
    assert.equal(watch.hoshiWatch.selectTextAt(1, 1, 16, false), 'seek:1');

    hit = second;
    assert.equal(watch.hoshiWatch.selectTextAt(1, 1, 16, false), 'list');

    hit = second.children[1];
    assert.equal(watch.hoshiWatch.selectTextAt(1, 1, 16, false), '言葉');
});
