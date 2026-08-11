# ProjectM Overlay

App Android che trasforma il telefono in un visualizzatore audio-reattivo
in stile MilkDrop, con il logo della tua band/progetto in sovraimpressione
che pulsa a ritmo di musica. Pensata per essere usata dal vivo, collegata
a una scheda audio USB.

Basata su [libprojectM](https://github.com/projectM-visualizer/projectm),
compilata nativamente per Android via NDK/CMake.

## Cosa fa

**Visualizer a schermo intero**
Migliaia di preset MilkDrop animati in tempo reale, reattivi all'audio in
ingresso, renderizzati via OpenGL ES direttamente sul telefono.

**Logo in sovraimpressione**
- Carichi un PNG dal telefono direttamente dal menu Impostazioni dell'app
- Dimensione regolabile (slider "zoom"), mantiene sempre le proporzioni
  originali dell'immagine
- Pulsa a ritmo di bassi/kick: puoi scegliere se la pulsazione agisce sulla
  scala, sull'opacità, o su entrambe, con intensità e velocità regolabili

**Browser preset**
- Preset inclusi nell'app + possibilità di importarne altri da una cartella
  del telefono (utile per collezioni grandi come il MegaPack di projectM)
- Stella per segnare i preferiti
- Bottone "+" per aggiungere un preset a una playlist (creabile al volo)
- **Finestra live**: tieni premuto sullo schermo per aprire un pannello che
  occupa la parte inferiore dello schermo, lasciando visibile sopra il
  visualizer che continua a girare dal vivo — ogni preset che tocchi nella
  lista si carica immediatamente, così lo vedi mentre scegli

**Modalità di scorrimento preset** (menu Impostazioni)
- Casuale tra i preferiti
- Casuale tra tutti i preset
- Playlist scelta, in ordine
- Playlist scelta, in ordine sparso
- Singolo preset fisso, cambio solo manuale

**Sorgente audio** (menu Impostazioni)
- Microfono interno del telefono
- Scheda audio USB collegata (con selezione del dispositivo)
- File audio riprodotto dall'app stessa (player interno)
- Gain regolabile sull'ingresso audio

**Controlli durante l'uso**
- Doppio tap a sinistra / destra sullo schermo → preset precedente / successivo
- Doppio tap al centro → apre le Impostazioni
- Tieni premuto → apre la finestra live sui preset (vedi sopra)
- Frecce ← → di una tastiera USB/bluetooth collegata → preset precedente/successivo

**Schermo intero immersivo**
Opzione per nascondere barra di stato e tasti di navigazione, per un utilizzo
pulito durante un live.

## Come funziona sotto il cofano

- Bridge JNI minimale (`native-lib.cpp`) tra Kotlin e l'API C di libprojectM:
  inizializzazione, invio del PCM audio, caricamento preset, render del
  frame. Tutta la logica di "quale preset, quando" vive in Kotlin
  (`PlaybackController.kt`), non nel codice nativo.
- Il cambio preset avviene sempre sul thread OpenGL (`glView.queueEvent`),
  perché caricare un preset richiede il contesto EGL corrente su quel
  thread per compilare gli shader.
- I preset importati da cartella vengono copiati nello storage interno
  dell'app (libprojectM richiede percorsi file reali, non riesce a leggere
  direttamente i `content://` URI di Android).
- La pulsazione del logo non usa il motore audio di projectM: un piccolo
  filtro passa-basso + envelope follower dedicato (`BassEnergyAnalyzer.kt`)
  stima l'energia dei bassi dal PCM grezzo.
- La cattura audio (`AudioEngine.kt`) usa `AudioRecord` per microfono/USB
  (con instradamento sul dispositivo scelto) e `Visualizer` agganciato alla
  sessione del `MediaPlayer` per il player interno.

## Limiti noti

- Preset importati in grande quantità: la copia nello storage interno
  richiede tempo e spazio disco corrispondente
- L'audio USB dipende dal supporto USB Audio Class del telefono e
  dell'interfaccia
- Il workflow builda un APK di debug (non firmato per il Play Store),
  pensato per uso personale/installazione diretta

## Crediti

Motore di rendering: [projectM](https://github.com/projectM-visualizer/projectm),
il progetto open source che reimplementa il visualizzatore MilkDrop
originale di Winamp.
