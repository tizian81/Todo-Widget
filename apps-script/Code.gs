/**
 * To-Do-Widget – Backend
 * Liest und ändert das Google Doc „To-Do“ (Abschnitte „Offen“ und „Erledigt“).
 *
 * Aufrufe (GET):  ?key=…&action=list
 *                 ?key=…&action=add&text=…
 *                 ?key=…&action=complete&text=…
 */

// ID deines To-Do-Dokuments (aus der URL docs.google.com/document/d/<ID>/edit)
const DOC_ID = '1No8m9ctdhS9C7WdcpDr0YvYTwxTS6c49_5Ur6bvU2WM';

// Eigenen langen Zufallsschlüssel eintragen (mind. 30 Zeichen, nur Buchstaben/Zahlen)
const SECRET = 'HIER_EIGENEN_SCHLUESSEL_EINTRAGEN';

function doGet(e) {
  const p = (e && e.parameter) || {};
  if (SECRET.indexOf('HIER_') === 0 || p.key !== SECRET) {
    return json_({ ok: false, error: 'unauthorized' });
  }

  const lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const body = DocumentApp.openById(DOC_ID).getBody();
    const action = p.action || 'list';
    const text = (p.text || '').trim();

    if (action === 'add') {
      if (!text) return json_({ ok: false, error: 'Leere Aufgabe' });
      addTo_(body, 'offen', text, null);
    } else if (action === 'complete') {
      const item = findOpen_(body, text);
      if (item) {
        // Erst in „Erledigt“ einfügen, dann aus „Offen“ entfernen
        addTo_(body, 'erledigt', text, item);
        item.removeFromParent();
      }
    } else if (action !== 'list') {
      return json_({ ok: false, error: 'Unbekannte Aktion' });
    }

    return json_({ ok: true, open: listOpen_(body) });
  } catch (err) {
    return json_({ ok: false, error: String(err && err.message || err) });
  } finally {
    lock.releaseLock();
  }
}

function json_(obj) {
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

function findOpen_(body, text) {
  const hit = itemsUnder_(body, 'offen').filter(function (x) {
    return x.el.getText().trim() === text;
  })[0];
  return hit ? hit.el : null;
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

  if (tpl) {
    const li = body.insertListItem(pos, tpl.copy());
    li.setText(text);
  } else {
    body.insertListItem(pos, text).setGlyphType(DocumentApp.GlyphType.BULLET);
  }
}
