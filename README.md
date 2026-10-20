# MiLauncher

Launcher Android TV minimaliste, sans pub ni recommandations, conçu pour une Xiaomi Mi Box S (Google TV).

- **Accueil** : grande horloge, météo détaillée (Open-Meteo, sans clé) et barre de 6 favoris en bas de l'écran.
- **Vue étendue** (flèche bas depuis la barre) : widgets réduits, barre en haut et toutes les apps dessous.
  Flèche haut, Retour ou Home ramènent à l'accueil.
- **Appui long** sur une app : ajouter/retirer des favoris, réordonner, masquer, infos.
- **Gérer les apps** : afficher ou masquer chaque app.
- Sans favoris, la barre affiche les apps les plus récemment utilisées.

Kotlin, Jetpack Compose et [Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv).

## Installation

```sh
./gradlew assembleRelease
adb connect <ip-de-la-box>:5555
adb install -r app/build/outputs/apk/release/app-release.apk

# Tri « récents d'abord » : autorise la lecture des statistiques d'usage.
adb shell appops set com.lilyan_lefevre.milauncher GET_USAGE_STATS allow
```

## Signature et CI

GitHub Actions (`.github/workflows/release.yml`) lance les tests et construit l'APK release à chaque push.
L'APK est disponible dans les artefacts du run ; pousser un tag `v*` publie aussi une release GitHub.

L'APK est signé avec une clé de release fixe, fournie par ces secrets du dépôt :

| Secret | Contenu |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | keystore encodé en base64 |
| `SIGNING_STORE_PASSWORD` | mot de passe du keystore |
| `SIGNING_KEY_ALIAS` | alias de la clé |
| `SIGNING_KEY_PASSWORD` | mot de passe de la clé |

En local, Gradle lit les variables `SIGNING_KEYSTORE` (chemin du fichier), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` et `SIGNING_KEY_PASSWORD`. Sans elles, la release est signée avec la clé debug.

## En faire l'écran d'accueil

Google TV ne propose pas de choisir le launcher par défaut : il faut désactiver l'accueil d'origine
et l'app qui le réactive.

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Pour revenir à l'accueil Google TV, remplacer `disable-user --user 0` par `enable`.
Une mise à jour système peut réactiver ces paquets : relancer alors les commandes ci-dessus.

## Tests

```sh
./gradlew testDebugUnitTest
```
