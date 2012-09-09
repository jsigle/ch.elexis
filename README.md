ch.elexis f�r 2.1.6.js / Verbesserungen und Erweiterungen des Elexis Kernmoduls
===============================================================================

Erweiterungen in elexis-base/ch.elexis, von von Elexis 2.1.6.dev zu nach 2.1.6.js:
Falls ich mich gerade richtig erinnere, mindestens folgende Verbesserungen und Erweiterungen:
Custom splash screen (zur Bearbeitung mit GIMP als *.xcf, zur Verwendung als *.bmp);
Kn�pfe und Men�punkte in Patientenliste und Adress-Details zum Kopieren einer oder mehrerer ausgew�hlter Adresse/n in die Zwischenablage
(mit Code, der die entsprechenden Textbl�cke erzeugt; je nach Aufgabenstellung Verwendung vorhandener Algorithmen f�r die erstellung der Postadresse oder eigener neuer Algorithmen);
robusteres Handling von Ergebnissen von Datenbankabfragen um die Apotheken-Bestellungs-Dialoge;
Kommentare und Code f�rs Ablauf-Monitoring.

�nderungen (nach grober Durchsicht) in diesen Ordnern oder Dateien;
andernfalls h�ufig auch mittels einer Suche nach "js" zu finden:

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
ch.elexis/build.properties                (Hier mglw. �nderungen, die im exported Product nach Bauen unter Windows Umlautfehler in mehreren Plugins verhindern?)
ch.elexis/plugin.xml                      (Mglw. nur f�r meine eigene Build/Export Umgebung erforderliche �nderungen)
ch.elexis/splash*.*                       (Custom splash screen als *.xcf und *.bmp; Originalversion als Fallback)

Getestet in einem auf mySQL basierenden System; seit der Fertigstellung
in einer Praxis �ber mehrere Monate produktiv genutzt, ohne dass auf diese
�nderungen zur�ckf�hrbare Fehler offensichtlich geworden w�ren.

Ausf�hrliche Informationen auf: http://www.jsigle.com/prog/elexis#ch.elexis_js

Status: BETA.
        Keinerlei Gew�hrleistung!
        Verwendung ausschliesslich auf eigene Verantwortung!
        Sachkundiger Anwender, eigene Funktionstests in
        unkritischer Umgebung und gute Backups dringend empfohlen!

Lizenz: Vorl�ufig Eclipse Public License v1.0 - Vorbehaltlich Pr�fung und Zustimmung f�r rose.gif