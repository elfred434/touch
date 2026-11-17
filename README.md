# Touch — raccourcis gestuels Android

Application Android personnelle inspirée des maquettes Stitch conservées dans `stitch_touch_gesture_shortcuts_android.zip`. Application sans publicité et sans assistant IA. Identifiant Android fixé à `com.elfred434.touch` : il doit rester identique pour que les mises à jour remplacent l’installation existante.

## État du projet

Le dépôt contient maintenant le premier socle Android natif : interface Compose sombre et menthe, configuration des gestes, sélection d’actions, réglage de la zone tactile, service d’accessibilité pour détecter les interactions, actions système de base et vérification des versions publiées sur GitHub.

Les fonctions Android sensibles ou dépendantes du constructeur (capture d’écran, flash, mode Ne pas déranger, zone exacte autour de l’encoche) doivent être testées sur plusieurs appareils. Certaines nécessitent une autorisation spécifique. Le service ne demande pas l’accès au contenu des fenêtres.

## Signature et sécurité

La même clé de signature doit signer toutes les versions diffusées. Le keystore est au format PKCS#12 et n’est pas dans le dépôt. GitHub Actions lit ces secrets du dépôt :

- `TOUCH_RELEASE_KEYSTORE_BASE64`
- `TOUCH_RELEASE_STORE_PASSWORD`
- `TOUCH_RELEASE_KEY_ALIAS`
- `TOUCH_RELEASE_KEY_PASSWORD`

Ne jamais ajouter le fichier `.p12`, ses mots de passe, ou un jeton GitHub au dépôt ou à l’APK. Le PAT sert uniquement à l’accès de maintenance au dépôt ; l’application consulte les versions publiques sans authentification.

## Publication et mise à jour

Chaque push sur `main` déclenche `.github/workflows/android-release.yml`. Le workflow compile et signe l’APK, l’attache à un GitHub Release `v1.0.N` sous le nom `Touch-1.0.N.apk` et l’archive aussi comme artifact du workflow. `N` augmente avec le numéro d’exécution Actions.

À l’ouverture, l’application vérifie la dernière version publiée via l’API publique GitHub. Si une version plus récente existe, elle propose le téléchargement et ouvre l’installateur système Android. Android exige l’accord de l’utilisateur et peut demander l’autorisation d’installer des applications provenant de cette source ; l’installation silencieuse n’est pas possible pour une application ordinaire.

## Compilation locale

Environnement requis : JDK 17, Android SDK, Gradle 8.13.

```bash
gradle :app:assembleDebug
```

La compilation/signature release se fait normalement dans GitHub Actions, qui injecte le keystore depuis les secrets sans le committer.
