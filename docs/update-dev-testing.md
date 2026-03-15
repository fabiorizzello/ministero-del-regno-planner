# Test update in dev

Per testare il flusso update senza creare una release remota ogni volta usa il task:

```powershell
./gradlew.bat :composeApp:runUpdateDev
```

Il task avvia l'app con queste impostazioni di default:

- `ministero.update.useLocalBuild=true`
- `ministero.update.devDisableInstallerCache=true`
- `ministero.update.devChunkDelayMs=35`
- `ministero.update.devChunkSizeBytes=65536`

Effetto pratico:

- l'app propone come update l'ultimo `.msi` locale generato
- il download locale non usa la cache in `exports/updates`
- la copia del file viene rallentata abbastanza da rendere visibili percentuale e velocita

Override utili:

```powershell
./gradlew.bat :composeApp:runUpdateDev -PupdateDev.chunkDelayMs=80 -PupdateDev.chunkSizeBytes=32768
```

Per testare un MSI specifico:

```powershell
./gradlew.bat :composeApp:runUpdateDev `
  -PupdateDev.localMsiPath="C:\path\scuola-di-ministero-0.1.19.msi" `
  -PupdateDev.localVersion=0.1.20
```

Note operative:

- se vuoi un test piu realistico della UI, usa un `.msi` di almeno qualche decina di MB
- la velocita mostrata nell'app e una media dall'inizio del download
- la finestra esterna finale non mostra MB/s, perche durante `msiexec /qn` non c'e un progresso affidabile da esporre
- se vuoi solo verificare che il progresso si muova senza rallentare troppo, i default del task sono pensati per stare intorno a qualche MB/s
