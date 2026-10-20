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

La version release est signée avec la clé debug, pour pouvoir l'installer sans keystore dédié.

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
