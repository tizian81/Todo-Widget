# To-Do Widget – Einrichtung

Android-Widget für deine Google-Docs-To-Do-Liste: anzeigen, abhaken (verschiebt nach „Erledigt“), hinzufügen.

## 1. Apps Script einrichten (Backend)

1. https://script.google.com → **Neues Projekt**, Namen z. B. „To-Do Widget“ vergeben.
2. Inhalt von `apps-script/Code.gs` komplett in `Code.gs` einfügen.
3. Bei `SECRET` einen eigenen langen Zufallsschlüssel eintragen (mind. 30 Zeichen, nur Buchstaben und Zahlen, z. B. aus einem Passwortmanager). Speichern.
4. **Bereitstellen → Neue Bereitstellung** → Zahnrad → **Web-App**
   - Ausführen als: **Ich**
   - Zugriff: **Jeder**
5. **Bereitstellen** → Zugriff autorisieren → dein Google-Konto wählen.
   Bei „Google hat diese App nicht überprüft“: **Erweitert → Zu To-Do Widget (unsicher) wechseln** → Zulassen. (Das ist dein eigenes Script.)
6. **Web-App-URL** kopieren (endet auf `/exec`).

Test im Browser: `<URL>?action=list&key=<DEIN_SCHLÜSSEL>` → sollte `{"ok":true,"open":[…]}` zeigen.

**Script später ändern:** Bereitstellen → Bereitstellungen verwalten → Stift → Version „Neue Version“ → Bereitstellen. So bleibt die URL gleich.

## 2. APK über GitHub bauen

1. Auf https://github.com einloggen → **New repository** → Name `TodoWidget`, **Private** → Create.
2. **uploading an existing file** → den **Inhalt** des entpackten Ordners hineinziehen (nicht den Ordner selbst) → Commit.
3. Prüfen, ob `.github/workflows/build.yml` mit hochgeladen wurde (versteckter Ordner, fehlt beim Hochladen oft). Falls nicht: **Add file → Create new file**, als Namen `.github/workflows/build.yml` eintippen, Inhalt der Datei einfügen → Commit.
4. Tab **Actions** → Build läuft automatisch (ca. 3–5 Min.). Grüner Haken = fertig.
5. Rechts **Releases** → neuester Build → `TodoWidget.apk`.

## 3. Auf dem Handy installieren

1. Auf dem Handy bei GitHub einloggen, `TodoWidget.apk` aus dem Release herunterladen und öffnen.
2. Beim ersten Mal „Unbekannte Apps installieren“ für den Browser bzw. „Eigene Dateien“ erlauben.
3. App **To-Do Widget** öffnen → Script-URL und Schlüssel eintragen → **Speichern & testen**.
4. Homescreen lange drücken → **Widgets** → **To-Do Widget** → platzieren, Größe anpassen.

## Bedienung

- Aufgabe antippen → abgehakt (wandert im Doc nach „Erledigt“)
- **+** → neue Aufgabe
- **Teilen** aus einer anderen App → „Zur To-Do-Liste“ → Text prüfen → Hinzufügen
- Ohne Netz: Abhaken/Hinzufügen funktioniert trotzdem, oben steht „N ausstehend“; wird automatisch nachgeholt, sobald wieder Netz da ist
- **✎** → Bearbeiten-Modus: Aufgabe antippen → Text ändern → Speichern (danach wieder normaler Modus)
- Doppelte Aufgaben: Beim Hinzufügen kommt ein Hinweis, nochmal tippen trägt sie trotzdem ein. Abhaken/Bearbeiten trifft genau den angetippten Eintrag.
- **↶** → letzte abgehakte Aufgabe zurück nach „Offen“ (erscheint nach dem Abhaken, merkt sich die letzten 10)
- **↻** → aktualisieren (sonst automatisch etwa alle 15 Min., wenn Netz da ist)
- „To-Do“-Titel antippen → Einstellungen
- Oben rechts steht die Uhrzeit der letzten Aktualisierung oder ein ⚠ mit Fehler.

## Hinweise

- Erledigte Aufgaben werden im Doc durchgestrichen. Bestehende Einträge einmalig per Funktion `erledigteDurchstreichen` im Script-Editor durchstreichen.
- Das Doc muss die Überschriften „Offen“ und „Erledigt“ behalten, Aufgaben sind Aufzählungspunkte darunter.
- Wer URL **und** Schlüssel kennt, kann die Liste lesen und ändern. Schlüssel also nicht weitergeben. Neuer Schlüssel: in `Code.gs` ändern, neue Version bereitstellen, in der App neu eintragen.
- Signaturschlüssel liegt in `app/signing.keystore`, damit Updates sich über die alte Version installieren lassen. Repo deshalb privat lassen.
- Updates: geänderte Dateien im Repo hochladen → neue APK unter Releases → einfach drüber installieren.
