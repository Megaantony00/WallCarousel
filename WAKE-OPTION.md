# Opzione "avanza all'accensione" (Home/Lock)

Stato: **implementata e verificata sul tablet** (16/11/2026).

## Richiesta

Un'opzione distinta per Home e Lock che faccia avanzare il carosello ogni volta
che il tablet viene acceso dallo standby (sblocco).

## Perché non basta un receiver nel manifest

Test eseguiti su SM-X626B (Android 16, API 36):

1. Receiver `WakeReceiver` dichiarato nel manifest con filtro
   `android.intent.action.USER_PRESENT`, `exported=false`.
   - Dopo un vero sblocco con PIN il cursore non è avanzato.
2. `USER_PRESENT` è inviato da **SystemUI (uid 10065)**, non dal system server:
   con `exported=false` il broadcast viene bloccato dal controllo di export.
3. Ripetuto il test con `exported=true`: il receiver viene risolto ma il sistema
   lo scarta comunque. Evidenza da `dumpsys activity broadcasts`:

   ```
   SKIPPED ... #37: ... state: SKIPPED/reason:skipped by policy at enqueue:
   Background execution not allowed: receiving Intent {
     act=android.intent.action.USER_PRESENT flg=0x24200010 xflg=0x4 }
   to com.wallcarousel.app/.schedule.WakeReceiver ... (manifest)
   ```

   `USER_PRESENT` non è nella lista delle eccezioni ai limiti di esecuzione in
   background di Android 8+, quindi un receiver nel manifest non lo riceve mai.
4. `ACTION_SCREEN_ON` è inviato con `FLAG_RECEIVER_REGISTERED_ONLY`: i receiver
   nel manifest non lo ricevono per definizione.
5. `adb shell am broadcast -a android.intent.action.USER_PRESENT` non è
   utilizzabile per i test: broadcast protetto, shell (uid 2000) non può
   inviarlo.

## Soluzione implementata

**Foreground service** (`WakeWatcherService`) con receiver dinamico registrato a
runtime, attivo solo quando serve.

- Tipo `specialUse` (unico tipo onesto; `dataSync` ha il limite di 6 ore,
  `shortService` dura 3 minuti).
- Notifica persistente obbligatoria: canale `wake_watcher` silenzioso,
  importanza minima, senza badge e vibrazione; sparisce quando il servizio si
  ferma.
- Il servizio esiste **solo** se almeno un target ha il trigger sblocco attivo e
  un carosello assegnato (`AppState.hasWakeTarget()`). `WorkScheduler.sync` lo
  avvia/ferma a ogni modifica; `WakeWatcher` evita richiami ripetuti nello
  stesso processo.
- Il trigger sblocco è un toggle indipendente: può convivere con intervallo e
  orari specifici sullo stesso target. Ogni trigger accoda il proprio lavoro;
  una guardia di 15 secondi evita che due trigger scattati insieme facciano
  avanzare due volte lo stesso schermo.
- `USER_PRESENT` → `WorkScheduler.enqueueWake()`: un solo work `KEEP` per volta
  che avanza Home e Lock in sequenza, così non girano due rendering insieme.
- `START_STICKY`; se il sistema riavvia il servizio e l'opzione non è più
  attiva, il servizio si ferma da solo.
- `BootReceiver` su `BOOT_COMPLETED` e `MY_PACKAGE_REPLACED` riavvia il servizio
  se serve.
- Android 13+: `POST_NOTIFICATIONS` richiesto al salvataggio dell'opzione; il
  servizio funziona comunque se l'utente nega (notifica nascosta, servizio
  visibile nel task manager).
- Play Store: dichiarazione `specialUse` nel Play Console. F-Droid non è
  interessata.

## File coinvolti

- `data/model/Models.kt`: `Schedule` con trigger indipendenti (`intervalMinutes`,
  `dailyMinutes`, `wake`), `AppState.hasWakeTarget()`.
- `data/StateRepository.kt`: migrazione schema 1 → 2.
- `data/model/SchedulePlanner.kt`: calcola solo il prossimo orario giornaliero.
- `schedule/WorkScheduler.kt`: `enqueueWake()`, `WAKE_WORK_NAME`, avvio/stop del
  watcher.
- `schedule/WakeWatcher.kt`: start/stop idempotenti.
- `schedule/WakeWatcherService.kt`: servizio foreground, receiver dinamico,
  notifica.
- `schedule/BootReceiver.kt`: riavvio dopo boot/aggiornamento.
- `schedule/ApplyWallpaperWorker.kt`: ramo `TRIGGER_WAKE`.
- `ui/carousel/CarouselEditorScreen.kt`: opzione nella dialog e richiesta del
  permesso notifiche.
- `AndroidManifest.xml`, `res/values/strings.xml`, `res/values-it/strings.xml`,
  `res/drawable/ic_wake.xml`.

## Test sul tablet (16/11/2026)

- Opzione visibile in italiano: "A ogni sblocco", con spiegazione della
  notifica.
- Home e Lock impostati su WAKE: dopo un vero sblocco con PIN i cursori sono
  passati **Home 1→2, Lock 2→1** con un solo `ApplyWallpaperWorker` eseguito
  (job schedulato e terminato con successo).
- Il servizio risulta foreground con `types=0x40000000` (`specialUse`), notifica
  `wake_watcher` `SILENT`, `ONGOING_EVENT`, importanza minima.
- Receiver dinamico per `USER_PRESENT` registrato (`dumpsys activity
  broadcasts`).
- Disattivando Home (Lock ancora WAKE) il servizio resta attivo; disattivando
  anche Lock il servizio si ferma e la notifica sparisce.
- Dopo reinstallazione (`MY_PACKAGE_REPLACED`) il servizio riparte da solo.
- Dopo un riavvio completo il servizio riparte da solo con
  `tempAllowListReason=BOOT_COMPLETED`; al successivo blocco/sblocco con PIN il
  cursore Home è passato 2→1 con un solo `ApplyWallpaperWorker`.
- Combinazione orari + sblocco: con Home su "Alle 08:00 · A ogni sblocco" lo
  sblocco ha avanzato il cursore 2→1 e il lavoro giornaliero è rimasto in coda
  per il giorno successivo (`TIME=+22h…`).
- Stato finale del tablet riportato a Home/Lock `MANUAL`.

## Test sul tablet (17/11/2026, keyguard "Scorri" senza PIN)

- Sblocco comandato da ADB (`KEYCODE_SLEEP`, `KEYCODE_WAKEUP`,
  `wm dismiss-keyguard`): con Home su "A ogni sblocco" il cursore è passato
  2→1 e `lastAppliedAt` è stato aggiornato, un solo `ApplyWallpaperWorker`.
- Riavvio completo (`adb reboot`): il servizio è ripartito da solo con
  `tempAllowListReason=BOOT_COMPLETED`, `isForeground=true`, notifica
  `wake_watcher`. Al primo sblocco il cursore è avanzato, ma il lavoro
  WorkManager è stato eseguito con circa 30 secondi di ritardo (fermato e
  riaccodato dal sistema). Lo sblocco successivo ha avanzato subito.
- Stato finale riportato a Home/Lock `MANUAL`, servizio fermo.

## Test ancora da fare

- Eventuale rimozione del servizio da parte di One UI con risparmio energetico
  aggressivo: verificare l'eccezione batteria se dovesse succedere.

## Limiti noti

- Dopo un riavvio il servizio riparte con `BOOT_COMPLETED`, ma il primo
  `USER_PRESENT` può arrivare prima che il servizio sia pronto. In quel caso lo
  sblocco non avanza il carosello e funziona dal successivo. Anche quando il
  servizio è pronto, WorkManager può ritardare il lavoro di decine di secondi
  (osservati ~30 s al primo sblocco dopo il riavvio).

## Note

- Il cursore avanza solo se lo sblocco arriva al servizio: se One UI mette in
  pausa l'app o l'utente forza la chiusura, il servizio può essere terminato
  dal sistema; `START_STICKY` mitiga ma non garantisce.
- I test di sblocco del 17/11/2026 sono stati eseguiti con keyguard "Scorri"
  (senza PIN), sbloccando via ADB con `wm dismiss-keyguard`.
