# PorticleGun – Fix-Liste

Befunde aus dem Code-Review. Status: `[ ]` offen · `[~]` in Arbeit · `[x]` erledigt · `[!]` gesperrt, nur nach Rücksprache.

> **Regel:** Das Einstellungs-GUI (`SettingsHandler`, Dropper-Menü) bleibt genau so, wie es ist. Keine Änderung an Layout, Slots, Bedienung oder Optik ohne vorherige Diskussion mit dem User.
Reihenfolge = Priorität. Unter jedem Punkt: Problem, Lösungsansatz, Notizen beim Abarbeiten.

## P1 – Datenverlust / funktionale Bugs

- [ ] **PG-01 Gravity Gun: Kisteninhalte liegen beim Tragen nur im RAM**
  - Ort: `handlers/gravity/GravityGun.java:404` (`storeContainerInventory`)
  - Problem: Die echte Kiste wird geleert, die Items hängen nur als `FixedMetadataValue` am FallingBlock. Stoppt oder crasht der Server während des Tragens, sind die Items weg.
  - Ansatz: Inhalt dauerhaft an der Entity speichern (PersistentDataContainer, serialisiert) oder Container nicht leeren. Beim Laden verwaiste FallingBlocks mit Inventar wiederherstellen.
  - Notizen:

- [ ] **PG-02 Kein `onDisable`: getragene Blöcke und Daten werden beim Stop nicht gesichert**
  - Ort: `PorticleGun.java`, `GravityGun.shutdown()`
  - Problem: `shutdown()` lässt gehaltene Blöcke nicht los und läuft nur beim Abschalten per Config.
  - Ansatz: `onDisable()` ergänzen: gehaltene Blöcke an aktueller Position absetzen (inkl. Inventar und Spawner-Typ), BlockDisplays entfernen, Portale speichern.
  - Notizen:

- [ ] **PG-03 Portale schließen im Settings-Menü wird nicht gespeichert**
  - Ort: `handlers/portals/SettingsHandler.java:61`
  - Problem: Der Doppelklick entfernt Portale nur aus der Map, nicht aus `portals.yml`. Nach Reload oder Neustart sind sie wieder da.
  - Ansatz: Gemeinsame Methode `ActivePortalsHandler.closePortals(gunID)`, die Speicher, Verlinkung und Datei konsistent aufräumt. Auch von `PorticleGunCommand.removeGunData` nutzen.
  - Notizen:

- [ ] **PG-14 Bedrock-Spieler über Geyser/Floodgate sauber unterstützen**
  - Ziel: Bedrock-Spieler (Handy, Konsole, Win10) können Portal Gun, Einstellungen und Gravity Gun genauso nutzen wie Java-Spieler.
  - Grundlage:
    - [ ] Floodgate-API als `provided`/optional einbinden, `softdepend: [floodgate, Geyser-Spigot]`. Hilfsklasse `BedrockSupport.isBedrock(player)`, die ohne Floodgate einfach `false` liefert (gleiches Muster wie `WorldGuardIntegration`)
    - [ ] Testumgebung: lokaler Paper-Server mit Geyser + Floodgate, Test mit Bedrock-Client (Desktop und Touch)
  - Steuerung (`handlers/portals/PortalOpenHandler.java`):
    - [ ] Primärportal hängt an `LEFT_CLICK_AIR`. Bedrock-Clients schicken Linksklick in die Luft nicht zuverlässig, auf Touch-Geräten praktisch gar nicht. Prüfen, was über Geyser ankommt, und für Bedrock eine alternative Belegung bauen (z. B. Fallback über `PlayerAnimationEvent` oder Umschalten Primär/Sekundär per Hand-Tausch bzw. Hotbar-Geste)
    - [ ] Sneak + Rechtsklick (Settings) und Sneak-Gesten der Gravity Gun mit Bedrock-Touch-Steuerung testen (Sneak ist dort ein Umschalter)
  - Einstellungs-GUI (`handlers/portals/SettingsHandler.java`) – **gesperrt, erst mit dem User besprechen:**
    - [!] Das Dropper-Inventar mit Slot-Klicks und „Doppelklick innerhalb 500 ms“ ist auf Touch kaum bedienbar. Für Bedrock-Spieler stattdessen ein natives Formular über die Floodgate/Cumulus-Forms-API anzeigen (Farben wählen, Form wechseln, Portale schließen mit Bestätigung)
  - Gun-Erkennung (`handlers/item/ItemHandler.java:92`):
    - [ ] Die Gun-ID steckt als `§`-codierte hochgestellte Unicode-Zeichen in der ersten Lore-Zeile. Bedrock interpretiert einige `§`-Codes anders (z. B. `§m`/`§n` als Farben) und rendert die Sonderzeichen evtl. als Kästchen. Prüfen, wie das über Geyser aussieht
    - [ ] Unabhängig davon die ID in den `PersistentDataContainer` verschieben (robuster für alle Spieler). Migration für bestehende Guns: alte Lore-ID beim ersten Halten auslesen und übertragen
  - Darstellung:
    - [ ] Farbige `DUST`-Partikel der Portale: prüfen, ob Geyser die Farben korrekt auf Bedrock überträgt, sonst Ersatz-Partikel für Bedrock
    - [ ] Gravity Gun nutzt `BlockDisplay`-Entities. Prüfen, wie Geyser Display-Entities darstellt, ggf. für Bedrock-Zuschauer auf FallingBlock-only zurückfallen
    - [ ] Titel/Actionbar (`TitleHandler`) und Menütitel mit Hex-Farben auf Bedrock prüfen
  - Teleport:
    - [ ] Teleport mit übernommener Geschwindigkeit (`TeleportationHandler`) auf Bedrock testen: Ruckeln oder verlorener Schwung durch Geysers Bewegungsübersetzung?
  - Doku:
    - [ ] README-Abschnitt „Bedrock / Geyser“ mit Steuerung für Bedrock-Spieler
  - Notizen:

- [ ] **PG-15 Bedrock-Controller: Schultertasten passen nicht zu den Farbseiten (Spielermeldung)**
  - Ort: `handlers/visualization/TitleHandler.java:65`, `handlers/portals/SettingsHandler.java:142`, `handlers/portals/PortalOpenHandler.java`
  - Problem: Die Anzeige geht von der Maus aus. Primär (Linksklick) wird links angezeigt (`◀` im Titel, Slot 3 im Menü), Sekundär (Rechtsklick) rechts (`▶`, Slot 5). Auf dem Bedrock-Controller ist es umgekehrt: der **rechte** Trigger (RT/R2) ist Angreifen und damit Linksklick, also Primär; der **linke** Trigger (LT/L2) ist Benutzen, also Sekundär. Für Controller-Spieler wirken Seiten und Farben dadurch vertauscht. In der Git-History gibt es dazu noch keinen Fix-Versuch.
  - Ansatz: Über Floodgate den Eingabemodus abfragen (`FloodgatePlayer#getInputMode()`: CONTROLLER / TOUCH / KEYBOARD_MOUSE). Bei CONTROLLER die Anzeige spiegeln: im Titel Sekundär links und Primär rechts. Das Einstellungs-GUI wird dabei **nicht** angefasst (Slot-Tausch im Menü nur nach Rücksprache). Das Schussverhalten bleibt gleich, damit die Farbe zur Taste passt. Input-Modus kann sich mitten im Spiel ändern, also bei jeder Anzeige neu abfragen. Touch separat prüfen (gehört zu PG-14).
  - Hängt ab von: PG-14 (Floodgate-Grundlage)
  - Notizen:

- [ ] **PG-16 Gravity Gun: gehaltene Blöcke verschwinden (Spielermeldung Bedrock)**
  - Ort: `handlers/gravity/GravityGun.java:98` (Tick-Task), `:258` (Spawn des FallingBlock), `:479` (`createVisualDisplayIfNeeded`)
  - Ursache 1 (sicher, betrifft alle Spieler): Der gehaltene Block ist ein normaler `FallingBlock`, der jeden Tick per `setVelocity` gezogen wird. Vanilla entfernt einen FallingBlock, der nach 600 Ticks (30 s) nicht gelandet ist, und lässt je nach `dropItem` höchstens ein Item fallen. `setTicksLived` wird nirgends zurückgesetzt. Wer einen Block länger als 30 s trägt, verliert ihn, bei Kisten samt Inhalt (siehe PG-01).
  - Ursache 2 (Verdacht, nur Bedrock): Bedrock rechnet FallingBlocks clientseitig mit eigener Schwerkraft und lässt sie dabei evtl. „landen“ und verschwinden, obwohl der Server sie noch hält. Bei Kisten hängt die Darstellung zusätzlich am `BlockDisplay` und damit an der Display-Entity-Unterstützung von Geyser.
  - Ansatz: `setTicksLived(1)` jeden Tick bzw. Lebenszeit anders absichern, `setDropItem(false)` setzen und beim Entfernen den Block sauber zurücksetzen. Langfristig den gehaltenen Block nicht mehr als physikalischen FallingBlock führen, sondern als reine Anzeige-Entity, die der Server per Teleport bewegt (Bedrock-Fallback beachten). Mit Geyser-Client reproduzieren: verschwindet er sofort, nach Sekunden oder nach ~30 s?
  - Notizen:

- [!] **PG-19 Einstellungs-GUI komplett neu und detailreich gestalten** – GESPERRT: Das GUI bleibt vorerst unverändert. Die Punkte unten sind nur Ideensammlung für eine spätere Diskussion, nichts davon ohne Freigabe umsetzen.
  - Ist-Zustand (`handlers/portals/SettingsHandler.java`): Ein 3×3-Dropper. Farben werden mit Pfeilen einzeln durchgeschaltet (Slots 0/6 bzw. 2/8), Vorschau in 3/5, Form-Banner in 7. Portale schließen per Doppelklick innerhalb von 500 ms auf Slot 4. Das Menü wird über eine Glasscheibe in Slot 1 erkannt, deren Name die Gun-ID versteckt enthält.
  - Ziel: Ein Menü, das ohne Erklärung verständlich ist, gut aussieht und auf Java und Bedrock gleich gut funktioniert.
  - Struktur und Technik:
    - [ ] Menü über eine eigene `InventoryHolder`-Klasse erkennen, die die Gun-ID trägt, statt über die Glasscheibe
    - [ ] Kleines Menü-Framework (Seiten, Buttons mit Click-Handler, Zurück-Navigation), damit alle Untermenüs gleich aufgebaut sind
    - [ ] Alle Klicks, Shift-Klicks, Nummerntasten und Drags (`InventoryDragEvent`) im Menü sauber abfangen, nicht nur `rawSlot < 9`
  - Hauptmenü (Truhe, 3–6 Reihen):
    - [ ] Zwei Hälften für Primär (links) und Sekundär (rechts), jeweils mit großer Farbvorschau, Status (offen/geschlossen), Welt, Koordinaten und Entfernung des Portals
    - [ ] Aktionen pro Portal: nur dieses Portal schließen, Farbe ändern
    - [ ] Formwahl (oval/rechteckig) mit Vorschau statt reinem Banner-Wechsel
    - [ ] „Beide Portale schließen“ mit eigener Bestätigungsseite statt 500-ms-Doppelklick
    - [ ] Info-Item mit Steuerungshilfe passend zum Eingabegerät (Maus, Controller, Touch; hängt an PG-14/15)
  - Farbwahl-Untermenü:
    - [ ] Alle verfügbaren Farben (inkl. Custom-Farben aus der Config) als Raster, statt sie einzeln durchzuschalten. Aktuelle Farbe markiert (Verzauberungs-Glanz), die Farbe des anderen Portals gesperrt bzw. gekennzeichnet
    - [ ] Farbname und Hex-Wert in der Lore, damit die Wahl nicht nur über die Farbe funktioniert (farbenblinde Spieler)
    - [ ] Mehrere Seiten, wenn es viele Custom-Farben gibt
  - Feedback und Feinschliff:
    - [ ] Passende Sounds für Hover/Klick, Bestätigen, Abbrechen und Fehler
    - [ ] Kurze Partikel-Vorschau der gewählten Farbe vor dem Spieler, während das Menü offen ist
    - [ ] Konsistente Lore: erste Zeile was es ist, dann Status, dann „Linksklick: …“ / „Rechtsklick: …“
    - [ ] Alle Texte über `messages.yml` (de/en), Menütitel inklusive
    - [ ] Bedrock: gleiche Funktionen als Floodgate-Formular (siehe PG-14), gleiche Struktur und Begriffe wie im Java-Menü
  - Notizen:

- [ ] **PG-20 Eigenes 3D-Modell der Portal Gun für Java und Bedrock (Resource-Pack als Overlay)**
  - Ziel: Wer die Gun hält, sieht ein aus Quadern modelliertes 3D-Modell statt eines Braustands, auf Java und über Geyser auf Bedrock. Das Pack funktioniert zusätzlich zu anderen Resource-Packs und ersetzt keine Vanilla-Dateien.
  - Java:
    - [ ] Server-/API-Mindestversion auf 1.21.4 anheben (Voraussetzung für die `item_model`-Komponente, aktuell wird gegen 1.21.1 gebaut, siehe PG-08)
    - [ ] Gun-Item bekommt `ItemMeta#setItemModel(porticlegun:portal_gun)`. Basis-Material bleibt der Braustand als Fallback für Spieler ohne Pack oder mit älterem Client
    - [ ] Pack mit eigenem Namespace `porticlegun` (`assets/porticlegun/items/portal_gun.json`, `models/item/portal_gun.json`, eigene Texturen). Keine Vanilla-Datei wird überschrieben, damit gibt es keine Konflikte mit anderen Packs
    - [ ] Modell aus Quader-Elementen bauen: weißer Korpus, dunkler Griff, drei Klauen vorne, leuchtender Kern. `display`-Transformationen für erste Person, dritte Person, GUI, Boden und Rahmen
    - [ ] Kern in der aktuellen Portalfarbe einfärben: `tints` mit Quelle `minecraft:custom_model_data`, Farben über die `CustomModelData`-Komponente am Item setzen (beim Farbwechsel und nach jedem Schuss aktualisieren)
    - [ ] Pack zusätzlich zum Server-Pack an Spieler schicken: `Player#addResourcePack(uuid, url, sha1, prompt, force)` (mehrere Packs stapelbar ab 1.20.3). In der Config: an/aus, Pflicht ja/nein, eigene URL
    - [ ] Hosting des Packs festlegen (offene Frage an den User): als Asset am GitHub-Release (baut die CI aus PG-13 inkl. SHA-1) oder über einen im Plugin eingebauten Mini-Webserver (braucht freien Port, geht bei vielen Hostern nicht)
  - Bedrock (Geyser):
    - [ ] Bedrock-Pack (`.mcpack`) mit Attachable und Geometrie für das 3D-Modell in der Hand, plus Icon für das Inventar. Erzeugung prüfen: GeyserMCs Tool „Rainbow“ bzw. Java→Bedrock-Konverter, oder eigenes Skript im Build
    - [ ] Geyser-Custom-Item-Mapping (Custom Items API v2, Zuordnung über `item_model` `porticlegun:portal_gun`)
    - [ ] Verteilung klären: Plugin legt Pack und Mapping beim ersten Start in `plugins/Geyser-Spigot/packs` bzw. `custom_mappings` ab und meldet, dass Geyser neu starten muss. Bei Geyser auf einem Proxy (Velocity/Bungee) muss der Admin die Dateien selbst kopieren. Prüfen, ob das sauber über die Geyser-API zur Laufzeit geht
    - [ ] Prüfen, ob sich die Farbeinfärbung auf Bedrock umsetzen lässt (ggf. eine Modellvariante pro Farbe)
  - Build:
    - [ ] Pack-Quellen unter `src/main/resourcepack/` (Java) und `src/main/bedrockpack/`. Maven/CI packt beide als Zip, berechnet SHA-1 und legt sie dem Release bei
  - Doku: Abschnitt in README und `docs/resourcepack.md` (siehe PG-18)
  - Notizen:

## P2 – Robustheit / Performance

- [ ] **PG-04 Eine unbekannte Farbe bricht das Laden aller Portale ab**
  - Ort: `handlers/LoadingHandler.java` (`loadPortals`)
  - Problem: `PortalColor.valueOf` wirft, wenn eine Custom-Farbe aus der Config entfernt wurde. Das beendet die `forEach`, alle folgenden Guns verlieren ihre Portale.
  - Ansatz: Jeden Eintrag einzeln absichern, auf Default-Farbe zurückfallen, Warnung loggen.
  - Notizen:

- [ ] **PG-05 Portale in später geladenen Welten werden zu null-Welt-Portalen**
  - Ort: `portals/Portal.java` (`extractLocation`)
  - Problem: `Bukkit.getWorld()` gibt `null` zurück, wenn z. B. Multiverse die Welt später lädt. Das Portal ist tot, `getPositionString()` wirft beim Speichern eine NPE.
  - Ansatz: Portale mit fehlender Welt zurückstellen und bei `WorldLoadEvent` nachladen, nie mit null-Welt speichern. Multiverse-Core als `softdepend` eintragen.
  - Notizen:

- [ ] **PG-06 Jeder `set()` schreibt `portals.yml` synchron auf die Platte**
  - Ort: `handlers/PersitentHandler.java:39`
  - Problem: Ganze YAML-Datei wird bei jedem Aufruf im Main-Thread gespeichert, ein Portalschuss = 3–4 Schreibvorgänge.
  - Ansatz: Dirty-Flag plus verzögertes Speichern (im Main-Thread serialisieren, asynchron schreiben). Synchron in `onDisable` (hängt mit PG-02 zusammen).
  - Notizen:

- [x] **PG-07 Build-Artefakte und IDE-Dateien sind eingecheckt**
  - Ort: `target/`, `.idea/`, `.DS_Store`, `PorticleGun.iml`
  - Problem: Keine `.gitignore`, `target/classes` ist veraltet (`Quaternion.class` ohne Quellcode).
  - Ansatz: `.gitignore` für Maven, IntelliJ und macOS anlegen, Dateien mit `git rm --cached` aus dem Index nehmen.
  - Notizen: `.gitignore` (Maven, IntelliJ, Eclipse/VS Code, macOS, `run/` für Testserver) angelegt, `target/`, `.idea/`, `.DS_Store`, `PorticleGun.iml` aus dem Index entfernt (lokal bleiben sie liegen).

- [ ] **PG-13 CI/CD-Pipeline mit GitHub Actions aufsetzen**
  - Ort: `.github/workflows/` (existiert noch nicht), Remote `github.com/nurkert/porticle-gun`
  - Problem: Es gibt keine automatischen Builds und Tests und keine reproduzierbaren Releases. Jars werden bisher lokal über ein IntelliJ-Artifact gebaut (`.idea/artifacts/PorticleGun_jar.xml`).
  - Ansatz:
    - [ ] CI-Workflow: bei Push auf `main` und bei jedem PR `mvn -B verify` mit Java 21 (Temurin) und Maven-Cache
    - [ ] Gebautes Jar als Workflow-Artifact hochladen (zum Testen von PR-Builds)
    - [ ] Release-Workflow: bei Tag `v*` Version aus dem Tag setzen, bauen, GitHub Release mit Jar und automatisch erzeugten Release Notes anlegen
    - [ ] Dependabot für Maven-Dependencies und GitHub Actions
    - [ ] Build-Badge in die README
    - [ ] Branch Protection auf `main` mit verpflichtendem CI-Check (macht der User in den GitHub-Einstellungen)
    - [ ] Optional: Upload nach Modrinth/Hangar per Action. Spigot hat keine Upload-API, das bleibt manuell.
  - Abhängigkeiten: PG-07 (`.gitignore`), PG-08 (eine Versionsquelle), PG-11 (Tests, damit `verify` etwas prüft)
  - Notizen:

- [ ] **PG-17 Repo gründlich aufräumen**
  - Ziel: Ein Repo, das man klonen, mit `mvn verify` bauen und ohne Vorwissen verstehen kann.
  - Teilaufgaben:
    - [ ] PG-07 (`.gitignore`, `target/`, `.idea/`, `.DS_Store`, `PorticleGun.iml` raus), PG-09 (Klassennamen), PG-10 (Formatierung) als Teil davon erledigen
    - [ ] Einheitliche Formatierung im ganzen Projekt (`.editorconfig`, ggf. Spotless o. Ä. im Maven-Build, damit CI das prüft)
    - [ ] Package-Struktur prüfen: `handlers/` ist ein Sammelbecken (Persistenz, Config, Debug, Audio, Gravity Gun). Z. B. `persistence/`, `gravity/`, `portals/`, `ui/` trennen
    - [ ] Statische Singletons (`ActivePortalsHandler`, `PersitentHandler`, `LoadingHandler`, `GunColorHandler`) schrittweise auf Instanzen umstellen, die das Plugin hält. Macht Reload, `onDisable` und Tests sauberer
    - [ ] Toten Code und Altlasten entfernen: auskommentierte Blöcke, `developMode`/`DebugHandler` prüfen, nicht genutzte Maven-Repos in der `pom.xml` (sonatype, papermc, jitpack)
    - [ ] Klären, ob `PluginJarRenamer` (Jar-Datei zur Laufzeit umbenennen) wirklich gebraucht wird, sonst entfernen
    - [ ] `PorticleGunCommand` (601 Zeilen) in Unterbefehle aufteilen
    - [ ] Bilder in `images/` (2,9 MB GIFs) prüfen und ggf. verkleinern
  - Notizen:

- [ ] **PG-18 Vernünftige README und Doku**
  - Problem: Die README besteht aus Bildern, einem Abschnitt „Recent Changes“ mit nur einem Eintrag und Config-Details. Es fehlen Installation, Befehle, Rechte und Steuerung. Die Bild-Links zeigen noch auf den alten Repo-Namen `nurkert/PorticleGun`. Es gibt keine Lizenz.
  - Teilaufgaben:
    - [ ] README neu aufbauen: Kurzbeschreibung mit GIF, Features, unterstützte Versionen (Server, Java, Geyser), Installation, Steuerung (Java **und** Bedrock-Touch/Controller, siehe PG-14/15), Befehle und Rechte als Tabelle, Badges (Build, Release, Spigot)
    - [ ] `docs/`-Ordner: `configuration.md` (alle Keys aus `config.yml` mit Default und Bedeutung), `worldguard.md`, `localization.md`, `bedrock.md`, `development.md` (Bauen, Testserver, Release-Ablauf aus PG-13)
    - [ ] `CHANGELOG.md` (Keep a Changelog), ab dem nächsten Release gepflegt bzw. aus Release Notes erzeugt
    - [ ] `LICENSE` – Lizenz muss der User festlegen
    - [ ] `CONTRIBUTING.md` sowie Issue- und PR-Templates unter `.github/`
    - [ ] Kommentare in `config.yml` und `messages.yml` vervollständigen, damit Server-Admins ohne README auskommen
    - [ ] Javadoc für die öffentlichen Klassen bzw. API (`MessageManager.setPlayerLanguage` u. a.)
  - Notizen:

## P3 – Aufräumen

- [x] **PG-08 Versionsangaben widersprechen sich**
  - Ort: `pom.xml`, `src/main/resources/plugin.yml`
  - Problem: pom `1.0.0` vs. plugin.yml `v1.2.2-SNAPSHOT`. `api-version: 1.19` passt nicht zu Java 21 und spigot-api 1.21.1 (Java 21 erst ab MC 1.20.5).
  - Ansatz: Version nur in der pom pflegen, per `${project.version}` filtern. `api-version` auf echte Mindestversion setzen.
  - Notizen: pom ist jetzt einzige Versionsquelle (`1.3.0-SNAPSHOT`), plugin.yml bekommt `${project.version}` per Filtering (Filtering nur noch für plugin.yml). spigot-api auf 1.21.4, `api-version: '1.21'`, zusätzlich Laufzeitprüfung in `onEnable`: unter 1.21.4 deaktiviert sich das Plugin mit klarer Meldung. Ungenutzte Maven-Repos (sonatype, papermc, jitpack) entfernt.

- [ ] **PG-09 Tippfehler in Klassennamen**
  - Ort: `PersitentHandler`, `VisualizationHanlder`
  - Ansatz: In `PersistentHandler` und `VisualizationHandler` umbenennen, Referenzen anpassen.
  - Notizen:

- [ ] **PG-10 GravityGun: gemischte Einrückung und auskommentierter Altcode**
  - Ort: `handlers/gravity/GravityGun.java`
  - Ansatz: Einheitlich Spaces, toten Code entfernen. Als eigener Commit ohne Logikänderung.
  - Notizen:

- [ ] **PG-11 Keine Tests, obwohl Surefire eingerichtet ist**
  - Ort: `handlers/portals/TeleportationHandler.java:229` (`createPortalBasis`/`transformVector`)
  - Ansatz: JUnit 5 ergänzen, Tests für Wand→Wand, Wand→Boden, Boden→Decke und Erhaltung des Betrags.
  - Notizen:

- [ ] **PG-12 Entity-Teleport-Check skaliert linear mit der Portalanzahl**
  - Ort: `handlers/portals/TeleportationHandler.java:70` (`tickEntityTeleports`)
  - Problem: Jeden Tick `getNearbyEntities` pro Portal, auch in ungeladenen Chunks.
  - Ansatz: Portale in ungeladenen Chunks überspringen. Erst messen, dann optimieren.
  - Notizen:
