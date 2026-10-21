# MiLauncher

Launcher Android TV minimaliste, sans pub ni recommandations, conçu pour une Xiaomi Mi Box S (Google TV).

![Démo sur la box : passage à la vue étendue, appui long, retour à l'accueil](docs/demo.gif)

[Vidéo en qualité d'origine](docs/demo.mp4), filmée sur la box (l'enregistreur de la box saute les images
identiques, la vidéo est donc plus rapide que la réalité).

| Accueil | Vue étendue |
|---|---|
| ![Accueil : grande horloge, météo et barre de favoris](docs/home.jpg) | ![Vue étendue : widgets réduits et toutes les apps](docs/expanded.jpg) |

![Menu de l'appui long sur une app](docs/menu.jpg)

- **Accueil** : grande horloge, météo détaillée (Open-Meteo, sans clé) et barre de 6 favoris en bas de l'écran.
- **Vue étendue** (flèche bas depuis la barre) : la page défile vers le haut, les widgets passent en version
  réduite, la barre remonte et toutes les apps apparaissent dessous.
  Flèche haut, Retour ou Home ramènent à l'accueil.
- **Appui long** sur une app : ajouter/retirer des favoris, réordonner, masquer, infos.
- **Gérer les apps** : afficher ou masquer chaque app.
- Sans favoris, la barre affiche les apps les plus récemment utilisées.
- Interface en anglais et en français, selon la langue de la box ; l'heure suit le réglage 12 h / 24 h.
- La météo utilise une position approximative déduite de l'adresse IP ([ipwho.is](https://ipwho.is)),
  mise en cache : aucune permission de localisation.

Kotlin, Jetpack Compose et [Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv).

## Installation

Télécharger l'APK de la dernière [release](../../releases/latest), ou le construire :

```sh
./gradlew assembleRelease
```

Puis l'installer sur la box :

```sh
adb connect <ip-de-la-box>:5555
adb install -r milauncher.apk

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

Android refuse de mettre à jour une app signée avec une autre clé : pour passer d'un build local signé en debug
à un APK de la CI, désinstaller d'abord l'app (`adb uninstall com.lilyan_lefevre.milauncher`), ce qui efface
les favoris, puis relancer la commande `appops` ci-dessus.

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
