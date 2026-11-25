/**
 * Tests für apps-script/Code.gs mit einem nachgebauten Google Doc.
 * Ausführen: node apps-script/test/code.test.js
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const assert = require('assert');

// ---------- Mini-Nachbau der Apps-Script-Dienste ----------
const ElementType = { PARAGRAPH: 'PARAGRAPH', LIST_ITEM: 'LIST_ITEM' };
const ParagraphHeading = { NORMAL: 'NORMAL', HEADING2: 'HEADING2' };

class El {
  constructor(type, text, heading) {
    this.type = type; this.text = text; this.heading = heading || ParagraphHeading.NORMAL;
    this.strike = false; this.parent = null;
  }
  getType() { return this.type; }
  getText() { return this.text; }
  setText(t) { this.text = t; return this; }
  asParagraph() { return this; }
  asListItem() { return this; }
  getHeading() { return this.heading; }
  setHeading(h) { this.heading = h; return this; }
  setGlyphType() { return this; }
  copy() { const c = new El(this.type, this.text, this.heading); c.strike = this.strike; return c; }
  editAsText() { const self = this; return { setStrikethrough(v) { self.strike = v; } }; }
  removeFromParent() {
    const b = this.parent; const i = b.children.indexOf(this);
    if (b.children.length === 1) throw new Error('Letztes Element kann nicht entfernt werden');
    b.children.splice(i, 1); this.parent = null;
  }
}

class Body {
  constructor() { this.children = []; }
  getNumChildren() { return this.children.length; }
  getChild(i) { return this.children[i]; }
  getChildIndex(el) {
    const i = this.children.indexOf(el);
    if (i < 0) throw new Error('Element ist kein Kind');
    return i;
  }
  _insert(i, el) { el.parent = this; this.children.splice(i, 0, el); return el; }
  appendParagraph(t) { return this._insert(this.children.length, new El(ElementType.PARAGRAPH, t)); }
  insertListItem(i, x) {
    return this._insert(i, typeof x === 'string' ? new El(ElementType.LIST_ITEM, x) : x);
  }
}

class Doc {
  constructor() { this.body = new Body(); this.ranges = []; }
  getBody() { return this.body; }
  newRange() {
    const els = [];
    return { addElement(e) { els.push(e); return this; }, build() { return els; } };
  }
  addNamedRange(name, els) {
    const doc = this;
    const nr = {
      name,
      getName() { return this.name; },
      getRange: () => ({
        getRangeElements: () => els.filter(e => e.parent).map(e => ({ getElement: () => e })),
      }),
      remove() { doc.ranges = doc.ranges.filter(r => r !== nr); },
    };
    this.ranges.push(nr);
    return nr;
  }
  getNamedRanges() { return this.ranges.slice(); }
}

function setup(lines, props) {
  const doc = new Doc();
  lines.forEach(l => {
    if (l.startsWith('# ')) doc.body.appendParagraph(l.slice(2)).setHeading(ParagraphHeading.HEADING2);
    else doc.body.insertListItem(doc.body.getNumChildren(), l.replace(/^- /, ''));
  });
  const store = Object.assign({ SECRET: 'geheim', DOC_ID: 'doc1', letztes_aufraeumen: String(Date.now()) }, props);
  const cache = {};
  const ctx = {
    console,
    DocumentApp: { ElementType, ParagraphHeading, GlyphType: { BULLET: 'BULLET' }, openById: () => doc },
    PropertiesService: { getScriptProperties: () => ({
      getProperty: k => (k in store ? store[k] : null),
      setProperty: (k, v) => { store[k] = v; },
    }) },
    CacheService: { getScriptCache: () => ({ get: k => cache[k] || null, put: (k, v) => { cache[k] = v; } }) },
    LockService: { getScriptLock: () => ({ waitLock() {}, releaseLock() {} }) },
    ContentService: {
      MimeType: { JSON: 'json' },
      createTextOutput: s => ({ setMimeType() { return JSON.parse(s); } }),
    },
  };
  vm.createContext(ctx);
  vm.runInContext(fs.readFileSync(path.join(__dirname, '..', 'Code.gs'), 'utf8'), ctx);
  const call = params => ctx.doPost({ parameter: Object.assign({ key: 'geheim' }, params) });
  const section = name => {
    const out = []; let on = false;
    doc.body.children.forEach(c => {
      if (c.type === ElementType.PARAGRAPH && c.heading !== ParagraphHeading.NORMAL) on = c.text.toLowerCase() === name;
      else if (on && c.type === ElementType.LIST_ITEM) out.push(c.text + (c.strike ? ' ~' : ''));
    });
    return out;
  };
  return { doc, ctx, store, call, section };
}

// ---------- Tests ----------
const tests = [];
const test = (name, fn) => tests.push([name, fn]);

test('falscher Schlüssel', () => {
  const t = setup(['# Offen', '- A', '# Erledigt']);
  const r = t.call({ key: 'falsch', action: 'list' });
  assert.strictEqual(r.error, 'unauthorized');
  assert.strictEqual(r.version, 2);
});

test('Platzhalter-Schlüssel wird abgelehnt', () => {
  const t = setup(['# Offen', '- A'], { SECRET: '' });
  assert.strictEqual(t.call({ key: 'HIER_EIGENEN_SCHLUESSEL_EINTRAGEN' }).error, 'unauthorized');
});

test('list liefert offene Aufgaben und Version', () => {
  const t = setup(['# Offen', '- A', '- B', '# Erledigt', '- C']);
  const r = t.call({ action: 'list' });
  assert.deepStrictEqual(r.open, ['A', 'B']);
  assert.strictEqual(r.version, 2);
  assert.strictEqual(r.ok, true);
});

test('add hängt an, leere Aufgabe ist Fehler', () => {
  const t = setup(['# Offen', '- A', '# Erledigt']);
  assert.deepStrictEqual(t.call({ action: 'add', text: 'B' }).open, ['A', 'B']);
  assert.strictEqual(t.call({ action: 'add', text: '  ' }).ok, false);
});

test('complete verschiebt nach Erledigt und streicht durch', () => {
  const t = setup(['# Offen', '- A', '- B', '# Erledigt', '- X']);
  assert.deepStrictEqual(t.call({ action: 'complete', text: 'A' }).open, ['B']);
  assert.deepStrictEqual(t.section('erledigt'), ['X', 'A ~']);
});

test('complete trifft das n-te gleichlautende Vorkommen', () => {
  const t = setup(['# Offen', '- Milch', '- Brot', '- Milch', '# Erledigt']);
  t.doc.body.children[3].strike = false; t.doc.body.children[3].marker = 'zweite';
  t.call({ action: 'complete', text: 'Milch', n: '1' });
  assert.deepStrictEqual(t.section('offen'), ['Milch', 'Brot']);
  assert.strictEqual(t.doc.body.children[1].marker, undefined, 'erste Milch bleibt offen');
});

test('uncomplete holt zurück, auch wenn gleiche Aufgabe noch offen', () => {
  const t = setup(['# Offen', '- Milch', '- Milch', '# Erledigt']);
  t.call({ action: 'complete', text: 'Milch' });
  assert.deepStrictEqual(t.section('offen'), ['Milch']);
  t.call({ action: 'uncomplete', text: 'Milch' });
  assert.deepStrictEqual(t.section('offen'), ['Milch', 'Milch']);
  assert.deepStrictEqual(t.section('erledigt'), []);
});

test('uncomplete entfernt Durchstreichen', () => {
  const t = setup(['# Offen', '- A', '# Erledigt']);
  t.call({ action: 'complete', text: 'A' });
  t.call({ action: 'uncomplete', text: 'A' });
  assert.deepStrictEqual(t.section('offen'), ['A']);
});

test('Erledigt als letztes Element im Doc kann zurückgeholt werden', () => {
  const t = setup(['# Offen', '# Erledigt', '- A']);
  t.call({ action: 'uncomplete', text: 'A' });
  assert.deepStrictEqual(t.section('offen'), ['A']);
});

test('edit ändert das n-te Vorkommen', () => {
  const t = setup(['# Offen', '- Milch', '- Milch', '# Erledigt']);
  const r = t.call({ action: 'edit', text: 'Milch', n: '1', new: 'Hafermilch' });
  assert.deepStrictEqual(r.open, ['Milch', 'Hafermilch']);
  assert.strictEqual(t.call({ action: 'edit', text: 'Milch', new: '' }).ok, false);
});

test('gleiche Aktions-ID wird nur einmal ausgeführt', () => {
  const t = setup(['# Offen', '# Erledigt']);
  t.call({ action: 'add', text: 'A', id: 'x1' });
  t.call({ action: 'add', text: 'A', id: 'x1' });
  assert.deepStrictEqual(t.section('offen'), ['A']);
  t.call({ action: 'add', text: 'A', id: 'x2' });
  assert.deepStrictEqual(t.section('offen'), ['A', 'A']);
});

test('unbekannte Aktion', () => {
  const t = setup(['# Offen']);
  assert.strictEqual(t.call({ action: 'quatsch' }).error, 'Unbekannte Aktion');
});

test('Aufräumen löscht nur alte erledigte Einträge', () => {
  const t = setup(['# Offen', '- A', '- B', '# Erledigt', '- Alt']);
  t.call({ action: 'complete', text: 'A' });
  // „A“ vor 40 Tagen erledigt, „Alt“ hat noch kein Datum
  const old = Date.now() - 40 * 864e5;
  t.doc.ranges[0].name = 'erledigt_' + old;
  t.ctx.erledigteAufraeumen();
  assert.deepStrictEqual(t.section('erledigt'), ['Alt']);
  assert.strictEqual(t.doc.ranges.length, 1, '„Alt“ bekommt ein Datum');
  t.ctx.erledigteAufraeumen();
  assert.deepStrictEqual(t.section('erledigt'), ['Alt'], 'frisch markiert bleibt stehen');
});

test('ERLEDIGT_TAGE=0 löscht nie', () => {
  const t = setup(['# Offen', '- A', '# Erledigt'], { ERLEDIGT_TAGE: '0' });
  t.call({ action: 'complete', text: 'A' });
  const old = Date.now() - 400 * 864e5;
  t.doc.ranges[0].name = 'erledigt_' + old;
  t.ctx.erledigteAufraeumen();
  assert.deepStrictEqual(t.section('erledigt'), ['A ~']);
});

test('Aufräumen läuft automatisch höchstens einmal täglich', () => {
  const t = setup(['# Offen', '# Erledigt', '- X'], { letztes_aufraeumen: '0' });
  t.call({ action: 'list' });
  assert.strictEqual(t.doc.ranges.length, 1, 'erster Aufruf markiert');
  t.doc.ranges = [];
  t.call({ action: 'list' });
  assert.strictEqual(t.doc.ranges.length, 0, 'zweiter Aufruf am selben Tag tut nichts');
});

test('GET funktioniert weiterhin (Browser-Test)', () => {
  const t = setup(['# Offen', '- A']);
  assert.deepStrictEqual(t.ctx.doGet({ parameter: { key: 'geheim', action: 'list' } }).open, ['A']);
});

let failed = 0;
tests.forEach(([name, fn]) => {
  try { fn(); console.log('✓ ' + name); }
  catch (e) { failed++; console.log('✗ ' + name + '\n   ' + e.message); }
});
console.log(`\n${tests.length - failed}/${tests.length} Tests bestanden`);
process.exit(failed ? 1 : 0);
