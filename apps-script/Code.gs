/**
 * To-Do-Widget – Backend
 * Liest und ändert das Google Doc „To-Do“ (Abschnitte „Offen“ und „Erledigt“).
 *
 * EINSTELLUNGEN stehen NICHT hier im Code, sondern in den Skripteigenschaften
 * (Zahnrad „Projekteinstellungen“ → ganz unten „Skripteigenschaften“):
 *   SECRET          dein geheimer Schlüssel (Pflicht)
 *   DOC_ID          ID des To-Do-Dokuments (aus docs.google.com/document/d/<ID>/edit)
 *   ERLEDIGT_TAGE   optional: Erledigtes nach so vielen Tagen löschen (Standard 30, 0 = nie)
 * Dadurch kann dieser Code bei Updates einfach komplett ersetzt werden.
 *
 * Aufrufe (App: POST, Test im Browser: GET), Parameter:
 *   key=…&action=list
 *   key=…&action=add&text=…
 *   key=…&action=complete&text=…
 *   key=…&action=uncomplete&text=…   (Rückgängig)
 *   key=…&action=edit&text=…&new=…
 * Optional: n=… wählt bei gleichlautenden Aufgaben das n-te Vorkommen (0 = erstes),
 *           id=… eindeutige Kennung; dieselbe Aktion wird nur einmal ausgeführt.
 */

// Version dieses Scripts – die App prüft sie (Api.REQUIRED_SCRIPT_VERSION).
const SCRIPT_VERSION = 2;

// Nur Platzhalter/Fallback – hier NICHTS eintragen, sondern Skripteigenschaften nutzen.
const SECRET = 'HIER_EIGENEN_SCHLUESSEL_EINTRAGEN';
const DOC_ID = '1No8m9ctdhS9C7WdcpDr0YvYTwxTS6c49_5Ur6bvU2WM';

const DONE_PREFIX = 'erledigt_';        // Named Ranges: erledigt_<Zeitstempel>
const CLEANUP_KEY = 'letztes_aufraeumen';

function doGet(e) { return handle_(e); }
function doPost(e) { return handle_(e); }

function config_(name, fallback) {
  const v = PropertiesService.getScriptProperties().getProperty(name);
  return (v && v.trim()) ? v.trim() : fallback;
}

function handle_(e) {
  const p = (e && e.parameter) || {};
  const secret = config_('SECRET', SECRET);
  if (secret.indexOf('HIER_') === 0 || p.key !== secret) {
    return json_({ ok: false, error: 'unauthorized' });
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const doc = DocumentApp.openById(config_('DOC_ID', DOC_ID));
    const body = doc.getBody();
    const action = p.action || 'list';
    const text = (p.text || '').trim();
    const n = parseInt(p.n, 10) || 0;

    // Wiederholung derselben Aktion (z. B. Antwort ging unterwegs verloren)?
    const cache = CacheService.getScriptCache();
    const opKey = p.id ? 'op_' + p.id : null;
    const already = opKey && cache.get(opKey);

    if (!already) {
      if (action === 'add') {
        if (!text) return json_({ ok: false, error: 'Leere Aufgabe' });
        addTo_(body, 'offen', text, null);
      } else if (action === 'complete') {
        const item = findOpen_(body, text, n);
        if (item) {
          // Erst in „Erledigt“ einfügen, dann aus „Offen“ entfernen
          const li = addTo_(body, 'erledigt', text, item);
          markDone_(doc, li, Date.now());
          removeItem_(body, item);
        }
      } else if (action === 'uncomplete') {
        // Zuletzt erledigten passenden Eintrag zurück nach „Offen“ holen
        // (funktioniert auch, wenn eine gleichlautende Aufgabe noch offen ist)
        const done = text ? findDone_(body, text) : null;
        if (done) {
          addTo_(body, 'offen', text, done);
          removeItem_(body, done);
        }
      } else if (action === 'edit') {
        const newText = (p['new'] || '').trim();
        if (!newText) return json_({ ok: false, error: 'Leere Aufgabe' });
        const item = findOpen_(body, text, n);
        if (item) {
          item.setText(newText);
          item.editAsText().setStrikethrough(false);
        }
      } else if (action !== 'list') {
        return json_({ ok: false, error: 'Unbekannte Aktion' });
      }
      if (opKey && action !== 'list') cache.put(opKey, '1', 21600); // 6 Stunden merken
    }

    // Höchstens einmal am Tag alte erledigte Einträge löschen
    try { cleanupIfDue_(doc); } catch (err) { console.warn(err); }

    return json_({ ok: true, open: listOpen_(body) });
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  } finally {
    lock.releaseLock();
  }
}

function json_(obj) {
  obj.version = SCRIPT_VERSION;
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/** Index der Überschrift „Offen“ bzw. „Erledigt“ (-1 wenn nicht vorhanden). */
function headingIndex_(body, name) {
  for (let i = 0; i < body.getNumChildren(); i++) {
    const el = body.getChild(i);
    if (el.getType() !== DocumentApp.ElementType.PARAGRAPH) continue;
    const par = el.asParagraph();
    if (par.getHeading() === DocumentApp.ParagraphHeading.NORMAL) continue;
    if (par.getText().trim().toLowerCase() === name) return i;
  }
  return -1;
}

/** Alle Listeneinträge direkt unter einer Überschrift (bis zur nächsten Überschrift). */
function itemsUnder_(body, name) {
  const start = headingIndex_(body, name);
  const items = [];
  if (start < 0) return items;
  for (let i = start + 1; i < body.getNumChildren(); i++) {
    const el = body.getChild(i);
    const type = el.getType();
    if (type === DocumentApp.ElementType.PARAGRAPH &&
        el.asParagraph().getHeading() !== DocumentApp.ParagraphHeading.NORMAL) break;
    if (type === DocumentApp.ElementType.LIST_ITEM) items.push({ index: i, el: el.asListItem() });
  }
  return items;
}

function listOpen_(body) {
  return itemsUnder_(body, 'offen')
    .map(function (x) { return x.el.getText().trim(); })
    .filter(function (t) { return t.length > 0; });
}

/** n-ter offener Eintrag mit diesem Text (sonst der erste, sonst null). */
function findOpen_(body, text, n) {
  const hits = itemsUnder_(body, 'offen').filter(function (x) {
    return x.el.getText().trim() === text;
  });
  const hit = hits[n || 0] || hits[0];
  return hit ? hit.el : null;
}

/** Zuletzt hinzugefügter passender Eintrag unter „Erledigt“ (oder null). */
function findDone_(body, text) {
  const hits = itemsUnder_(body, 'erledigt').filter(function (x) {
    return x.el.getText().trim() === text;
  });
  return hits.length ? hits[hits.length - 1].el : null;
}

/** Fügt einen Eintrag am Ende des Abschnitts ein (legt die Überschrift notfalls an). */
function addTo_(body, name, text, template) {
  let h = headingIndex_(body, name);
  if (h < 0) {
    body.appendParagraph(name === 'offen' ? 'Offen' : 'Erledigt')
      .setHeading(DocumentApp.ParagraphHeading.HEADING2);
    h = body.getNumChildren() - 1;
  }
  const items = itemsUnder_(body, name);
  // Vorlage für Formatierung: übergebenes Item, sonst ein bestehendes aus „Offen“
  const tpl = template ||
    (itemsUnder_(body, 'offen')[0] || itemsUnder_(body, 'erledigt')[0] || {}).el || null;
  const pos = items.length ? items[items.length - 1].index + 1 : h + 1;

  let li;
  if (tpl) {
    li = body.insertListItem(pos, tpl.copy());
    li.setText(text);
  } else {
    li = body.insertListItem(pos, text).setGlyphType(DocumentApp.GlyphType.BULLET);
  }
  // Erledigtes durchgestrichen, Offenes normal
  li.editAsText().setStrikethrough(name === 'erledigt');
  return li;
}

/** Entfernt einen Eintrag; das letzte Element eines Docs darf nicht gelöscht werden. */
function removeItem_(body, el) {
  if (body.getChildIndex(el) === body.getNumChildren() - 1) body.appendParagraph('');
  el.removeFromParent();
}

/** Merkt sich unsichtbar (Named Range), wann ein Eintrag erledigt wurde. */
function markDone_(doc, li, time) {
  doc.addNamedRange(DONE_PREFIX + time, doc.newRange().addElement(li).build());
}

function cleanupIfDue_(doc) {
  const props = PropertiesService.getScriptProperties();
  const last = Number(props.getProperty(CLEANUP_KEY) || 0);
  if (Date.now() - last < 24 * 60 * 60 * 1000) return;
  props.setProperty(CLEANUP_KEY, String(Date.now()));
  erledigteAufraeumen(doc);
}

/**
 * Löscht Einträge unter „Erledigt“, die älter als ERLEDIGT_TAGE (Standard 30) sind.
 * Läuft automatisch höchstens einmal täglich; kann auch von Hand ausgeführt werden.
 * Einträge ohne Datum (z. B. von vor diesem Update) bekommen beim ersten Lauf das
 * heutige Datum und werden entsprechend später gelöscht.
 * Gelöschtes bleibt über Datei → Versionsverlauf wiederherstellbar.
 */
function erledigteAufraeumen(openDoc) {
  const days = parseInt(config_('ERLEDIGT_TAGE', '30'), 10);
  // Beim Ausführen im Editor wird kein Dokument übergeben
  const doc = (openDoc && openDoc.getBody) ? openDoc : DocumentApp.openById(config_('DOC_ID', DOC_ID));
  const body = doc.getBody();
  const now = Date.now();
  const maxAge = (isNaN(days) ? 30 : days) * 24 * 60 * 60 * 1000;

  const doneItems = itemsUnder_(body, 'erledigt');
  const doneIdx = {};
  doneItems.forEach(function (x) { doneIdx[x.index] = x.el; });

  // Vorhandene Markierungen auswerten
  const dated = {};      // Index im Body → Zeitstempel
  const toRemove = [];
  doc.getNamedRanges().forEach(function (nr) {
    const name = nr.getName();
    if (name.indexOf(DONE_PREFIX) !== 0) return;
    const time = Number(name.substring(DONE_PREFIX.length));
    const els = nr.getRange().getRangeElements();
    let idx = -1;
    try {
      if (els.length) idx = body.getChildIndex(els[0].getElement());
    } catch (err) { idx = -1; }
    if (idx < 0 || !doneIdx[idx]) { nr.remove(); return; }   // Eintrag weg oder nicht mehr erledigt
    dated[idx] = time;
    if (days > 0 && now - time > maxAge) toRemove.push({ idx: idx, nr: nr });
  });

  // Einträge ohne Datum markieren
  doneItems.forEach(function (x) {
    if (!(x.index in dated) && x.el.getText().trim()) markDone_(doc, x.el, now);
  });

  // Von hinten nach vorne löschen, damit die Indizes stimmen
  toRemove.sort(function (a, b) { return b.idx - a.idx; }).forEach(function (r) {
    r.nr.remove();
    removeItem_(body, doneIdx[r.idx]);
  });
}

/**
 * Einmalig von Hand ausführen (im Editor oben auswählen → „Ausführen“):
 * streicht alle Einträge unter „Erledigt“ durch und entfernt das
 * Durchstreichen bei allen Einträgen unter „Offen“.
 */
function erledigteDurchstreichen() {
  const body = DocumentApp.openById(config_('DOC_ID', DOC_ID)).getBody();
  itemsUnder_(body, 'erledigt').forEach(function (x) {
    if (x.el.getText().length) x.el.editAsText().setStrikethrough(true);
  });
  itemsUnder_(body, 'offen').forEach(function (x) {
    if (x.el.getText().length) x.el.editAsText().setStrikethrough(false);
  });
}
