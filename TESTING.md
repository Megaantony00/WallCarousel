# Test sul tablet

Dispositivo: Samsung Galaxy Tab S10 FE+ 5G (SM-X626B), Android 16 (API 36), One UI,
1800×2880 px, densità 320 (2x), lingua italiana, build debug installata via ADB.

## Layout adattivo

Soglia: barra laterale (rail) da 600 dp di larghezza, altrimenti barra inferiore.

### Finestra freeform ridimensionata

1. Avvio con `am start --windowingMode 5` (freeform) e ridimensionamento a
   900×1400 px (≈450 dp): layout compatto con barra inferiore
   (Sfondi · Caroselli · Impostazioni).
2. Apertura dell'editor del carosello nella finestra stretta, poi
   ridimensionamento a 2500×1400 px (≈1250 dp): il layout passa alla barra
   laterale e l'editor resta aperto sullo stesso carosello, con assegnazioni e
   sfondi al loro posto.

### Split-screen

1. Split-screen avviato dalle app recenti (WallCarousel a destra,
   Impostazioni a sinistra): finestra 1432×1770 px = 716 dp → barra laterale.
2. Rotazione del display in verticale durante lo split: finestra 889×2850 px =
   445 dp → barra inferiore. L'app non blocca l'orientamento e lo stato
   dell'editor è preservato.
3. Ritorno all'orientamento orizzontale e uscita dallo split: layout a barra
   laterale, stesso carosello aperto.

## TalkBack

- Pulsanti icona con `content-desc` in italiano: "Indietro", "Elimina
  carosello", "Sposta su", "Sposta giù", "Rimuovi".
- Righe della pianificazione: `checkable=true`, `role=Switch`, altezza minima
  48 dp, etichetta testuale letta da TalkBack.
- TalkBack è stato disattivato al termine del test.

## Cancellazione di tutti i dati

1. Backup di `files/` con `run-as ... tar` (state.json + 2 immagini).
2. Impostazioni → Elimina tutti i dati → conferma.
3. Verifica: `state.json` riporta schema 2 con liste vuote, `files/wallpapers/`
   vuota, lavori WorkManager annullati, servizio di sblocco fermo.
4. UI: "La libreria è vuota" in Sfondi, "Nessun carosello" in Caroselli.
5. Ripristino del backup: 2 sfondi, carosello "Test tablet", Home e Lock
   manuali.

## Pianificazioni combinate e sblocco

Vedi `WAKE-OPTION.md`: migrazione schema 1 → 2, trigger indipendenti,
combinazione orari + sblocco, guardia anti-doppio.

## Sblocco e riavvio

Con keyguard "Scorri" (PIN rimosso per il debug) e Home su "A ogni sblocco":

1. Blocco schermo (`KEYCODE_SLEEP`), risveglio (`KEYCODE_WAKEUP`) e sblocco
   (`wm dismiss-keyguard`): cursore 2→1, `lastAppliedAt` aggiornato.
2. `adb reboot`: dopo il boot il servizio è ripartito da solo
   (`tempAllowListReason=BOOT_COMPLETED`, `isForeground=true`). Il primo
   sblocco ha avanzato il cursore con circa 30 s di ritardo (WorkManager fermato
   e riaccodato); lo sblocco successivo è stato immediato.
3. Stato riportato a Home/Lock `MANUAL`, servizio fermo.

## Release (R8)

- `assembleRelease` con R8 e riduzione risorse: primo tentativo installato sul
  tablet e **crash all'avvio** (`NoSuchMethodException` su
  `androidx.work.impl.WorkDatabase_Impl.<init>`).
- Aggiunta la regola `-keep class * extends androidx.room.RoomDatabase { <init>(); }`
  in `app/proguard-rules.pro`: la build firmata con una chiave di test si avvia,
  importa uno sfondo, lo conserva dopo un riavvio dell'app e decodifica
  `state.json` (regole kotlinx.serialization verificate).
- APK release con R8: circa 2,9 MB (debug: circa 10 MB).
- La chiave di test è stata usata solo per la verifica e non fa parte del
  progetto; il tablet è stato riportato alla build debug con i dati di prova.

## Da eseguire

- Samsung DeX: richiede l'attivazione manuale dal tablet (pannello rapido o
  impostazioni); non esiste un comando ADB affidabile per attivarlo. Il layout è
  già coperto dai test freeform/split-screen, che usano lo stesso meccanismo di
  configurazione.
- Pop-up view di One UI: stessa classe di finestre ridimensionabili del
  freeform, già verificata.
