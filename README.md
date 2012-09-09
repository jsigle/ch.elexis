ch.elexis für 2.1.6.js / Verbesserungen und Erweiterungen des Elexis Kernmoduls
===============================================================================

Erweiterungen in elexis-base/ch.elexis, von von Elexis 2.1.6.dev zu nach 2.1.6.js:
Falls ich mich gerade richtig erinnere, mindestens folgende Verbesserungen und Erweiterungen:
Custom splash screen (zur Bearbeitung mit GIMP als *.xcf, zur Verwendung als *.bmp);
Knöpfe und Menüpunkte in Patientenliste (PatientenView) und Kontakte-Liste (KontakteView) zum Kopieren einer oder mehrerer ausgewählter Adresse/n in die Zwischenablage
(mit Code, der die entsprechenden Textblöcke erzeugt; je nach Aufgabenstellung Verwendung vorhandener Algorithmen für die erstellung der Postadresse oder eigener neuer Algorithmen);
robusteres Handling von Ergebnissen von Datenbankabfragen um die Apotheken-Bestellungs-Dialoge;
Kommentare und Code fürs Ablauf-Monitoring.

Änderungen (nach grober Durchsicht) in diesen Ordnern oder Dateien;
andernfalls häufig auch mittels einer Suche nach "js" zu finden:

ch.elexis/src/data/Bestellung.java
ch.elexis/src/data/Kontakt.java
ch.elexis/src/data/messages.properties
ch.elexis/util/viewers/DefaulLabelProvider.java
ch.elexis/util/views/BestellView.java
ch.elexis/util/views/KontakteView.java
ch.elexis/util/PatientenListeView.java
ch.elexis/util/RezepteView.java
ch.elexis/util/messages*.properties
ch.elexis/rsc/plaf/classic/icons/rose.gif (ohne dieses File werden *alle* nachfolgend geladenen Icons zu klein dargestellt, auf Wunsch gerne Ersatz durch Platzhalter?)
ch.elexis/build.properties                (Hier mglw. Änderungen, die im exported Product nach Bauen unter Windows Umlautfehler in mehreren Plugins verhindern?)
ch.elexis/plugin.xml                      (Mglw. nur für meine eigene Build/Export Umgebung erforderliche Änderungen)
ch.elexis/splash*.*                       (Custom splash screen als *.xcf und *.bmp; Originalversion als Fallback)

Getestet in einem auf mySQL basierenden System; seit der Fertigstellung
in einer Praxis über mehrere Monate produktiv genutzt, ohne dass auf diese
Änderungen zurückführbare Fehler offensichtlich geworden wären.

Ausführliche Informationen auf: http://www.jsigle.com/prog/elexis#ch.elexis_js

Status: BETA.
        Keinerlei Gewährleistung!
        Verwendung ausschliesslich auf eigene Verantwortung!
        Sachkundiger Anwender, eigene Funktionstests in
        unkritischer Umgebung und gute Backups dringend empfohlen!

Lizenz: Vorläufig Eclipse Public License v1.0 - Vorbehaltlich Prüfung und Zustimmung für rose.gif