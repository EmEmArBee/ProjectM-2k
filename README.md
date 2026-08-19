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
  (e lo puoi rimuovere in qualsiasi momento con un bottone dedicato)
- Dimensione regolabile (slider "zoom"), mantiene sempre le proporzioni
  originali dell'immagine
- Opacità base regolabile, indipendente dalla pulsazione
- Pulsa a ritmo di bassi/kick: puoi scegliere se la pulsazione agisce sulla
  scala, sull'opacità, o su entrambe, con intensità e velocità regolabili
- Soglia di rilevamento del "colpo" di basso regolabile (più bassa = più
  sensibile, utile per generi con bassi meno marcati)

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
- Opzione indipendente: far sì che il doppio tap avanti/indietro peschi
  sempre un preset a caso tra tutti quelli disponibili, ignorando la
  modalità impostata sopra
- **Cambio a tempo di beat**: invece di una durata fissa in secondi, il
  preset cambia ogni N colpi di basso rilevati (soglia + periodo di
  refrattarietà, non è un vero beat-tracker musicale ma funziona bene per
  house/techno/hip-hop dove il kick è marcato)
- Durata del crossfade tra un preset e il successivo regolabile

**Backup**
Preferiti e playlist si possono esportare/importare come file JSON dal menu
Impostazioni — utile prima di reinstallare l'app o per portarseli su un
altro telefono (a patto di re-importare gli stessi preset nella stessa
struttura di cartelle).

**Sorgente audio** (menu Impostazioni)
- Microfono interno del telefono
- Scheda audio USB collegata (con selezione del dispositivo)
- File audio riprodotto dall'app stessa (player interno)
- Gain regolabile sull'ingresso audio

**Controlli durante l'uso**
- Doppio tap a sinistra / destra sullo schermo → preset precedente / successivo
- Doppio tap al centro → apre le Impostazioni
- Tieni premuto al centro → aggiunge/rimuove rapidamente dai preferiti il
  preset in riproduzione in quel momento (con una stellina di conferma)
- Tieni premuto ai lati → apre la finestra live sui preset (vedi sopra)
- Frecce ← → di una tastiera USB/bluetooth collegata → preset precedente/successivo

**Schermo intero immersivo**
Opzione per nascondere barra di stato e tasti di navigazione, per un utilizzo
pulito durante un live.

## Da dove prendere i preset

I file preset definiscono le visualizzazioni tramite pixel shader ed
equazioni/parametri in stile Milkdrop. La libreria projectM (e quindi
questa app) **non include alcun preset di suo**.

**Download diretto dall'app** (menu Impostazioni → "Scarica pacchetti
preset"): un tap e il pacchetto scelto viene scaricato e scompattato da
solo nella cartella preset importati, con percentuale di avanzamento a
schermo — utile per chi non vuole scaricare/scompattare manualmente sul
telefono. Pacchetti disponibili, ospitati su
[marcobottecchia.it/ProjectM](https://www.marcobottecchia.it/ProjectM/):

| Pacchetto | Dimensione | Contenuto |
|---|---|---|
| MilkDrop 135k+ Presets MegaPack | 4,49 GB | oltre 130.000 preset, texture incluse — scaricalo solo con tempo, connessione e spazio liberi in abbondanza |
| Cream of the Crop Pack | 32,10 MB | ~10.000 preset selezionati da Jason Fletcher, il pacchetto predefinito di projectM |
| Classic projectM Presets | 8,54 MB | poco più di 4.000 preset dalle versioni precedenti di projectM |
| Collezioni storiche projectM | 8,39 MB | bltc201, Milkdrop 1 e 2, projectM, tryptonaut, yin |
| Base Milkdrop Texture Pack | 3,37 MB | texture di base, consigliato insieme a qualsiasi altro pacchetto |
| Milkdrop 2 Presets (originali) | 1,38 MB | la collezione originale distribuita con Milkdrop e Winamp |

L'app controlla lo spazio libero prima di scaricare e non fa mai crashare
per un download fallito (connessione assente, spazio insufficiente): mostra
semplicemente un messaggio d'errore.

**Mirror/sorgenti originali** (di riserva, se il sito sopra fosse
irraggiungibile — vanno scaricati e importati manualmente con "Importa
preset da cartella"):
- [Cream of the Crop](https://github.com/projectM-visualizer/presets-cream-of-the-crop),
  [Classic projectM](https://github.com/projectM-visualizer/presets-projectm-classic),
  [Milkdrop original](https://github.com/projectM-visualizer/presets-milkdrop-original),
  [Texture pack](https://github.com/projectM-visualizer/presets-milkdrop-texture-pack),
  [En D](https://github.com/projectM-visualizer/presets-en-d) — repository ufficiali di projectM
- Collezioni storiche: http://spiegelmc.com/pub/projectm_presets.zip
- MegaPack: https://drive.google.com/file/d/1DlszoqMG-pc5v1Bo9x4NhemGPiwT-0pv/view
  (Google Drive, non scaricabile automaticamente dall'app per via del
  meccanismo di conferma che Drive richiede sui file grandi)

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

## Compilazione (via browser, senza Git né terminale)

La build dell'APK è automatica su GitHub Actions: il workflow scarica da
solo il codice di libprojectM e compila tutto, senza bisogno di Android
Studio, Git o terminale in locale.

1. Crea un repository vuoto su [github.com](https://github.com)
2. Carica il contenuto di questa cartella trascinandolo nella pagina
   "upload files" di GitHub
3. Vai sulla tab **Actions**: la build parte da sola (10-20 minuti la prima
   volta, poi più veloce grazie alla cache)
4. Scarica l'APK dagli **Artifacts** del run completato e installalo sul
   telefono (serve abilitare "Installa app sconosciute" per l'app che usi
   per aprire il file)

Per aggiornare l'app dopo una modifica: ricarichi i file cambiati con
"Add file → Upload files" (o l'editor ✏️ sul singolo file), e GitHub
Actions ricompila da sola.

## Limiti noti

- Preset importati in grande quantità: la copia nello storage interno
  richiede tempo e spazio disco corrispondente (l'app controlla lo spazio
  libero prima di iniziare e non crasha più se lo spazio finisce o un file
  è illeggibile — mostra un errore e si ferma lì)
- L'audio USB dipende dal supporto USB Audio Class del telefono e
  dell'interfaccia
- Su dispositivi lenti (head unit economiche, telefoni datati) è disponibile
  una **modalità prestazioni** (menu Impostazioni) che riduce la risoluzione
  interna del visualizzatore: l'app prova a rilevare da sola se attivarla al
  primo avvio (RAM, numero di core, solo 32 bit — una stima grezza, non un
  benchmark vero), ma resta sempre modificabile a mano
- Caricare un logo PNG molto pesante non rischia più di far crashare l'app
  per memoria insufficiente: l'immagine viene ridotta già in fase di
  decodifica alla dimensione che serve davvero
- Il workflow builda un APK di debug (non firmato per il Play Store),
  pensato per uso personale/installazione diretta
- **Modalità di fusione stile Photoshop per il logo** (scherma, brucia,
  inverti...) non sono implementate: il visualizzatore usa `GLSurfaceView`,
  che disegna su un livello grafico separato dal resto dell'interfaccia, e
  non è possibile applicare modalità di fusione tra un `View` normale (il
  logo) e il suo contenuto con gli strumenti standard di Android. Servirebbe
  passare a `TextureView` (fattibile, ma un cambiamento di architettura più
  corposo, non incluso per ora)

## Crediti

Motore di rendering: [projectM](https://github.com/projectM-visualizer/projectm),
il progetto open source che reimplementa il visualizzatore MilkDrop
originale di Winamp.
