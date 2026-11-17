# Pubblicazione

Checklist per pubblicare WallCarousel su GitHub, F-Droid e Google Play.

## 1. Versione

- `app/build.gradle.kts`: incrementa `versionCode` e aggiorna `versionName`.
- `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` e
  `fastlane/metadata/android/it-IT/changelogs/<versionCode>.txt`: note di
  rilascio per F-Droid.
- `README.md`: aggiorna le limitazioni se cambiano.

## 2. Firma

Il repository non contiene chiavi. Crea una chiave di upload una volta sola e
conservala fuori dal progetto:

```sh
keytool -genkeypair -v -keystore upload.jks -alias wallcarousel \
  -keyalg RSA -keysize 4096 -validity 10000
```

Poi crea `keystore.properties` nella root del progetto (è già in `.gitignore`):

```properties
storeFile=/percorso/assoluto/upload.jks
storePassword=...
keyAlias=wallcarousel
keyPassword=...
```

Non stampare mai il contenuto di `keystore.properties` o della chiave, non
committarli e non caricarli su servizi online.

## 3. Build e verifica

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

Senza `keystore.properties` il file prodotto è `app-release-unsigned.apk`: è
quello che usa il build server di F-Droid.

Controlla a mano su un dispositivo reale:

- avvio a freddo, importazione di uno sfondo, assegnazione e "Applica ora";
- pianificazione a intervallo, a orari e a ogni sblocco;
- esportazione e cancellazione dei dati;
- tema chiaro/scuro e lingua inglese/italiana.

## 4. GitHub

1. Crea il repository pubblico `wallcarousel` con licenza GPL-3.0 (dal sito,
   oppure con `gh repo create wallcarousel --public --source=. --push`).
2. Primo commit con il contenuto della cartella del progetto.
3. Tag annotato `v1.0` e release con l'APK firmato e il file `.apk.idsig` se
   generato.
4. Abilita i workflow solo se servono: il progetto non ha CI obbligatoria.

## 5. F-Droid

Il metadata dell'app sta in `fastlane/metadata/android/`. Il recipe per
`fdroiddata` è in `fdroid/com.wallcarousel.app.yml`: sostituisci
`<commit-del-tag>` con lo SHA del commit del tag e invia una merge request a
`https://gitlab.com/fdroid/fdroiddata`.

Punti già pronti per F-Droid:

- nessun permesso internet, nessun tracker, nessuna pubblicità;
- build riproducibile con `assembleRelease` senza chiavi;
- licenza GPL-3.0-or-later dichiarata in testa a ogni file sorgente;
- `fastlane` con descrizioni e changelog in inglese e italiano.
- Schermate inglesi già pronte in
  `fastlane/metadata/android/en-US/images/` (phone e tablet 10").

## 6. Google Play

- Crea l'app con package `com.wallcarousel.app`.
- Compila il modulo Data safety: nessun dato raccolto o condiviso.
- Dichiara il foreground service `specialUse` con la motivazione: il servizio
  resta attivo solo mentre l'utente ha attivato l'avanzamento a ogni sblocco e
  serve a ricevere `USER_PRESENT`, che Android non consegna ai receiver
  dichiarati nel manifest.
- Carica l'APK/AAB firmato con la chiave di upload.
- Non sono necessari permessi di storage: le foto passano dal Photo Picker.
