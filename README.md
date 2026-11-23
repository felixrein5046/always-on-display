# Always On Display (Google Pixel)

Android-App (Kotlin, Android 11+), die beim Ausschalten des Bildschirms eine schwarze
Always-On-Anzeige mit Uhr, Datum, Akkustand und Benachrichtigungssymbolen zeigt.

## Funktionen
- Startet automatisch, wenn das Display ausgeschaltet wird (auch nach Neustart)
- Uhr, Datum, Akku (⚡ beim Laden), Symbole der Apps mit Benachrichtigungen
- Antippen (einstellbar: einmal oder doppelt) → Entsperren · Power-Taste → Bildschirm wirklich aus
- Widgets: nächster Wecker, nächster Termin, Musiksteuerung und ein beliebiges Android-Widget (z. B. Wetter)
- Einbrennschutz: Inhalt verschiebt sich jede Minute
- Näherungssensor: Display geht in der Tasche aus
- Optionen: nur beim Laden, Helligkeit, automatisch ausschalten, Mindest-Akkustand
- Pausiert während Telefonaten

## Bauen mit GitHub (ohne Installation am Mac)
1. Auf [github.com](https://github.com) anmelden (kostenloses Konto) → oben rechts **+** → **New repository**,
   Name z. B. `always-on-display`, **Public** wählen (dann ist der Download aufs Handy ohne Login möglich) → **Create repository**
2. Auf der neuen Seite auf **„uploading an existing file“** klicken
3. Im Finder den Ordner „Dennis App“ öffnen und **⌘ + Shift + .** drücken, damit der versteckte Ordner `.github` sichtbar wird
4. **Alle** Dateien und Ordner (auch `.github`, `app`, `gradle`) ins Browserfenster ziehen → **Commit changes**
5. Reiter **Actions** öffnen: Der Lauf „APK bauen“ startet von selbst und dauert ca. 3–5 Minuten (grüner Haken = fertig)
6. Auf dem Pixel die Repo-Seite öffnen → **Releases** (rechts bzw. unten) → `AlwaysOnDisplay.apk` antippen
7. Installation erlauben, wenn Android nach „Unbekannte Apps installieren“ fragt

Hinweis: Jeder Build wird mit einem neuen Test-Schlüssel signiert. Für ein Update vorher die alte Version deinstallieren.

## Bauen mit Android Studio
1. [Android Studio](https://developer.android.com/studio) installieren
2. Diesen Ordner öffnen (`File → Open`) – Studio lädt Gradle & SDK automatisch
3. Pixel per USB verbinden (Entwickleroptionen → USB-Debugging an) und ▶ Run drücken,
   oder `Build → Build APK(s)` und die APK aus `app/build/outputs/apk/` aufs Handy kopieren

## Einrichten auf dem Handy
1. App öffnen, die drei Berechtigungen antippen und erlauben
   (Über anderen Apps einblenden ist Pflicht, die anderen empfohlen)
2. Schalter „Always On Display aktiv“ einschalten
3. Pixel-eigenes AOD ausschalten: Einstellungen → Display → Sperrbildschirm → „Uhrzeit und Infos immer anzeigen“

## Hinweis
Apps von Drittanbietern dürfen nicht in den echten Stromspar-Anzeigemodus des Systems zeichnen.
Diese App lässt deshalb das Display mit niedriger Helligkeit an. Auf dem OLED-Display des Pixels
sind schwarze Pixel aus, der Mehrverbrauch ist trotzdem höher als beim System-AOD.
