# Outil de l'icône de l'appli Android

Fabrique l'icône de l'appli Canapilou (Tilou au pop-corn sur le mauve du canapé) à partir de l'image d'origine rangée ici. Choix détaillés dans le journal du projet (décisions 71 et 72).

L'appli n'utilise pas ce dossier : elle ne lit que les fichiers déjà fabriqués dans `android/app/src/main/res/`.

## Contenu

- `icone.py` : l'outil (commandes `fabriquer` et `apercu`).
- `tilou-popcorn-source.png` : l'image d'origine, générée par l'utilisateur (1086 × 1448 px, fond blanc). C'est la même image que le logo de l'en-tête du site.
- `apercu.png` : planche d'aperçu, créée par la commande `apercu`. Elle n'est pas envoyée sur GitHub (voir `.gitignore`).

## Prérequis

Python 3 avec Pillow (avec WebP, inclus dans les versions habituelles), NumPy et SciPy, comme pour `outils/badges/` :

```
python -m pip install pillow numpy scipy
```

Non vérifié sur le PC de l'utilisateur. Claude peut aussi lancer l'outil dans son propre espace de travail.

Les commandes se lancent depuis la racine du dépôt.

## Refaire l'icône

1. Modifier si besoin les réglages en tête de `icone.py` (voir plus bas), ou remplacer `tilou-popcorn-source.png` par une nouvelle image (même genre : Tilou seul, fond blanc uni, sans ombre).

2. Fabriquer les fichiers :

   ```
   python outils/icone/icone.py fabriquer
   ```

   L'outil écrit directement dans `android/app/src/main/res/` :
   - `mipmap-<densité>/ic_launcher_tilou.png` : l'image de Tilou (premier plan de l'icône, 108 dp, de 108 à 432 px) ;
   - `drawable/ic_launcher_background.xml` : le fond uni ;
   - `mipmap-<densité>/ic_launcher.webp` et `ic_launcher_round.webp` : anciennes icônes, pour Android 7 et avant (jamais utilisées par cette appli, qui demande Android 8 au moins).

   Pour écrire ailleurs (essai) : `--sortie DOSSIER`.

   Les deux fichiers `mipmap-anydpi/ic_launcher.xml` et `ic_launcher_round.xml`, qui assemblent le fond et l'image, sont écrits à la main ; l'outil n'y touche pas.

3. Regarder l'aperçu :

   ```
   python outils/icone/icone.py apercu
   ```

   L'aperçu montre l'icône en rond et en carré arrondi, sur un fond d'écran clair et sombre, puis en petit.

4. Lancer l'appli dans l'émulateur. Si l'écran d'accueil garde l'ancienne icône, désinstaller l'appli puis la relancer. Envoyer ensuite sur GitHub (`android/`, `outils/icone/`).

## Réglages

En tête de `icone.py`, avec les valeurs du 23/11/2026 (couleur choisie par l'utilisateur, le reste choisi par Claude, à revoir après des tests) :

- **Couleur du fond** : `FOND = '#C294D4'`, mauve du canapé pris sur `img/tilou-canape.png`.
- **Détourage** : le fond est fait des zones presque blanches (composantes au-dessus de 232, écart entre elles sous 18) reliées au bord de l'image ; les bords sont adoucis d'après le mélange entre le contour de Tilou (125, 3, 58) et le blanc. L'outil signale une zone de fond enfermée dans le dessin.
- **Mise en place** : la documentation Android garantit que les 66 × 66 dp du centre de l'icône ne sont jamais coupés par la forme choisie par le téléphone. Le haut de Tilou (oreilles, tête, pop-corn : au-dessus de la ligne 1000 de l'image d'origine, `Y_LIMITE`) est agrandi autant que possible en restant dans un cercle de 64 dp au centre (`RAYON_SUR_DP = 32`). Le buste descend sous la zone visible, pour que la coupe ne se voie jamais.
- **Version « à thème » (monochrome)** : aucune pour l'instant (voir le journal, décision 72).

## Licence

L'outil est sous la licence du dépôt (AGPL-3.0). L'image de Tilou a été générée par l'utilisateur, comme les autres images de Tilou du site.
