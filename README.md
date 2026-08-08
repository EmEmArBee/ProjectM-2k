# ProjectM Overlay (Android)

App Android che mostra il visualizer MilkDrop di [libprojectM](https://github.com/projectM-visualizer/projectm)
a schermo intero, con un logo PNG in overlay che può pulsare a ritmo di bassi.
La build dell'APK è **completamente automatica su GitHub Actions**: non serve
Android Studio, non serve Git, non serve il terminale. Tutto dal browser.

## Cosa fa l'app

- Visualizer projectM a schermo intero (OpenGL ES, via JNI su libprojectM).
- Logo PNG caricabile **dall'app stessa** (menu Impostazioni → "Carica logo PNG"),
  con pulsazione a ritmo di bassi/kick: puoi scegliere se la pulsazione agisce
  sulla **scala**, sull'**opacità** o su **entrambe**, con intensità e velocità
  regolabili.
- Browser dei preset con **stella preferiti** e possibilità di creare
  **playlist** di preset (selezione multipla → salva con nome).
- 5 modalità di scorrimento preset (menu Impostazioni → "Modalità di
  scorrimento preset"):
  1. Casuale tra i preferiti
  2. Casuale tra tutti i preset
  3. Playlist scelta, in ordine
  4. Playlist scelta, in ordine sparso
  5. Singolo preset fisso, cambio solo manuale
- Cambio preset manuale: **doppio tap** sullo schermo (destra = successivo,
  sinistra = precedente) oppure **frecce ← →** di una tastiera USB collegata.
- Sorgente audio selezionabile: **microfono interno**, **scheda audio USB**
  collegata al telefono, oppure un **file audio riprodotto dall'app** (player
  interno).
- Preset: puoi tenerne alcuni inclusi nell'app (cartella `assets/presets`) e
  **importarne altri da una cartella del telefono** in qualsiasi momento
  (menu Impostazioni → "Importa preset da cartella") — utile per collezioni
  grandi tipo il MegaPack di projectM.

## Passo 1 — Crea il repository su GitHub (dal browser)

1. Vai su [github.com](https://github.com) ed effettua il login (o registrati).
2. In alto a destra clicca sul **+** → **New repository**.
3. Dai un nome al repo (es. `projectm-overlay`), lascialo pubblico o privato
   come preferisci, **NON** spuntare "Add a README file" (carichiamo noi tutto).
4. Clicca **Create repository**.

## Passo 2 — Carica i file del progetto

1. Estrai lo zip `ProjectMOverlay.zip` che ti ho dato, sul PC.
2. Nella pagina del repo appena creato (è vuoto), clicca sul link
   **"uploading an existing file"** (in mezzo alla pagina).
3. Apri la cartella estratta `ProjectMOverlay` in Esplora File di Windows,
   seleziona **tutto il contenuto** (Ctrl+A dentro la cartella, non la cartella
   stessa) e trascinalo nella pagina di upload di GitHub nel browser.
   GitHub mantiene la struttura delle sottocartelle automaticamente.
4. Scendi in fondo, scrivi un messaggio tipo "Primo caricamento" e clicca
   **Commit changes**.

   *(Opzionale ma comodo: se vuoi qualche preset già incluso nell'app fin dal
   primo avvio, prima di caricare aggiungi qualche file `.milk` dentro
   `app/src/main/assets/presets/` — altrimenti va benissimo anche importarli
   dopo, dal telefono, con il pulsante "Importa preset da cartella".)*

## Passo 3 — Lascia compilare GitHub Actions

1. Nel repo, clicca sulla tab in alto **Actions**.
2. Se è la prima volta, GitHub potrebbe chiederti conferma: clicca
   **"I understand my workflows, go ahead and enable them"**.
3. Dovresti vedere partire (o poterlo avviare tu) il workflow **Build APK**.
   Clicca sopra per vedere il progresso.
4. La prima build richiede più tempo (10–20 minuti circa) perché compila
   libprojectM da sorgente insieme al bridge nativo. Le successive, se non
   cambi il codice C++, sono più veloci grazie alla cache.
5. Se qualcosa va storto, il log della build ti dice dove — puoi anche solo
   incollarmelo e ti aiuto a interpretarlo.

## Passo 4 — Scarica e installa l'APK sul telefono

1. Alla fine della build (segno di spunta verde ✅), apri quel run e scorri
   fino alla sezione **Artifacts** in basso.
2. Scarica `projectm-overlay-debug` (è uno zip contenente l'APK), estrailo
   sul telefono (o trasferiscilo dal PC).
3. Sul telefono, apri il file `.apk`: Android chiederà di abilitare
   **"Installa app sconosciute"** per l'app che stai usando per aprirlo
   (File, Chrome, ecc.) — concedilo solo per quell'app se ti viene chiesto,
   poi procedi con l'installazione.

Ogni volta che vuoi aggiornare l'app, ripeti il Passo 2 (carichi i file
modificati dal browser, con "Add file → Upload files" sui singoli file da
sostituire, oppure editandoli direttamente su GitHub con la matita ✏️ su
ogni file) e GitHub Actions ricompila da sola.

## Come funziona sotto il cofano (se ti interessa)

- Non uso un git submodule per libprojectM (richiederebbe comandi Git in
  locale): è il workflow `.github/workflows/build.yml` stesso a scaricare
  il codice sorgente di projectM in `app/src/main/cpp/projectm` ad ogni
  build, con un secondo step `actions/checkout`.
- `native-lib.cpp` è un bridge JNI minimale: inizializza projectM, gli passa
  il PCM audio, gli dice quale file preset caricare e gli chiede di
  disegnare un frame. **Tutta** la logica di "quale preset, quando, in che
  ordine" vive in Kotlin (`PlaybackController.kt`), non nel codice nativo:
  così è più facile modificarla senza toccare C++.
- I preset importati da SAF (Storage Access Framework) vengono **copiati**
  nello storage interno dell'app (`filesDir/presets/imported`), perché
  libprojectM ha bisogno di percorsi file reali per aprirli e non può
  leggere direttamente i `content://` URI di Android.
- La pulsazione del logo non usa l'audio-engine di projectM: c'è un piccolo
  filtro passa-basso + envelope follower dedicato (`BassEnergyAnalyzer.kt`)
  che stima l'energia dei bassi dal PCM grezzo, indipendente dal renderer.
- La cattura audio (`AudioEngine.kt`) usa `AudioRecord` per microfono/USB
  (con `setPreferredDevice` per instradare sulla scheda USB scelta) e
  `Visualizer` agganciato alla sessione del `MediaPlayer` per il player
  interno.

## Limiti noti / cose da tenere d'occhio

- **API C di projectM**: le firme in `native-lib.cpp` sono quelle dell'ultima
  versione stabile nota; se una build futura di projectM cambia l'header
  `projectM.h`, potrebbe servire un piccolo aggiustamento.
- **Audio USB**: dipende dal supporto USB Audio Class del telefono e
  dell'interfaccia; se il device non compare nell'elenco in Impostazioni,
  prova a ricollegarla dopo aver aperto l'app (Android a volte enumera i
  device USB con un piccolo ritardo).
- **Import di collezioni enormi** (es. il MegaPack da 130k preset): la copia
  nello storage interno può richiedere tempo e spazio disco corrispondente;
  per uso quotidiano conviene tenere una selezione più mirata.
- Il workflow builda solo un **APK debug** (non firmato per il Play Store):
  perfetto per uso personale/installazione diretta.
