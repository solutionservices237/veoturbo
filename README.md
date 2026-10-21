# Veo Turbo Capture
APK simple : ouvre l'application PWA dans une WebView, vous vous connectez normalement,
et l'outil enregistre requêtes API (fetch/XHR/WebSocket), ressources, cookies, stockage local
et fichiers du front. Export JSON dans Download/VeoTurbo.

## Générer l'APK (sans installer Android Studio)
1. Créez un dépôt GitHub privé et poussez ce dossier (branche main).
2. Onglet Actions > "Build APK" (se lance seul, ou "Run workflow").
3. Téléchargez l'artefact "VeoTurboCapture-APK", installez app-debug.apk sur le téléphone Android.

## Utilisation
Saisir l'adresse de l'application > OK > se connecter > naviguer dans toutes les fonctions > Exporter.
Les champs mot de passe sont masqués (***) dans les corps de requêtes enregistrés.
