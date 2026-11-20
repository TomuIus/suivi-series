# Outil des images de badges

Fabrique les images de Tilou affichées sur les badges du site (`img/badges/<icône>.png`), à partir des images détourées rangées dans `sources/`. Choix détaillés dans le journal du projet (décision 47).

Le site n'utilise pas ce dossier : il ne lit que les images déjà fabriquées dans `img/badges/`.

## Contenu

- `badges.py` : l'outil (découper, repérer les yeux, fabriquer, aperçu).
- `badges.json` : la liste des badges, avec leur image source et leurs réglages particuliers.
- `sources/` : une image détourée par badge, nommée d'après le code d'icône du badge (`clock`, `flame`…). Ce sont les images générées par l'utilisateur, découpées et détourées, en pleine taille.
- `apercu.png` : planche d'aperçu, créée par la commande `apercu`. Elle n'est pas envoyée sur GitHub (voir `.gitignore`).

## Prérequis

Python 3 avec Pillow (9.1 ou plus récent), NumPy et SciPy :

```
python -m pip install pillow numpy scipy
```

Non vérifié sur le PC de l'utilisateur. Claude peut aussi lancer l'outil dans son propre espace de travail.

Toutes les commandes se lancent depuis la racine du dépôt.

## Ajouter ou refaire un badge

1. **Générer l'image** avec l'image de Tilou jointe, par exemple :

   > Le lapin rose de l'image jointe (même personnage, même style plat, même contour framboise foncé), vu de face, en buste, même cadrage que les images précédentes. Fond blanc uni, sans ombre, sans texte, sans logo. Tous les éléments ont un contour foncé et restent proches de lui.

   Ajouter ensuite l'élément du badge (objet tenu, accessoire porté, ou éléments autour de lui). Plusieurs figures peuvent être sur une même image, séparées par du blanc.

2. **Découper et détourer** :

   ```
   python outils/badges/badges.py decouper planche.png --sortie essais
   ```

   Chaque figure devient `essais/planche-1.png`, `planche-2.png`… L'outil signale une figure qui touche le bord gauche, droit ou haut de l'image (élément peut-être coupé). Le bas du buste touche souvent le bord : c'est normal. Ajouter `--une` si l'image ne contient qu'une figure qui serait découpée à tort (par exemple des étincelles séparées par du blanc).

3. **Ranger la figure choisie** sous `outils/badges/sources/<icône>.png`.

4. **Nouveau badge seulement** : l'ajouter dans `badges.json`, et dans la base (partie `badges_def` du fichier SQL de la progression, avec le même code dans le champ `icone`).

5. **Vérifier les yeux** (étalon de taille et de position de Tilou) :

   ```
   python outils/badges/badges.py yeux outils/badges/sources/<icône>.png
   ```

   Si un œil est caché, les mesurer à la main et les écrire dans `badges.json` : `"yeux": [x, y, écart]`, avec `x` et `y` en fraction de la largeur et de la hauteur de l'image et l'écart en pixels. Exemple : `world` (le globe cache un œil).

6. **Fabriquer l'image du site** :

   ```
   python outils/badges/badges.py fabriquer <icône>
   ```

   Sans code, tous les badges sont refaits.

7. **Regarder l'aperçu** :

   ```
   python outils/badges/badges.py apercu
   ```

   L'aperçu montre chaque image sur sa pastille, en clair et en sombre, plus un badge « non obtenu ».

8. Tester le site, puis envoyer sur GitHub (`img/badges/`, `outils/badges/`). Les images n'ont pas de numéro de version : après un changement, le navigateur peut garder l'ancienne jusqu'à 10 minutes (Ctrl+F5 pour forcer).

## Réglages

En tête de `badges.py`, avec les valeurs de la v31 (choix de Claude, à revoir après des tests) :

- **Géométrie** : rond de 64 px CSS avec un contour de 2 px, 24 px au-dessus pour les oreilles, images à 3 pixels par pixel CSS (192 × 264 px). Ces valeurs doivent suivre `.badge-pastille` dans `css/style.css` (4 rem × 5,5 rem, rond de 4 rem). Pour changer la taille, modifier les deux ensemble, puis refaire tous les badges.
- **Cadrage** : écart entre les yeux = 0,40 diamètre ; yeux à 0,57 diamètre sous le haut du rond ; largeur de l'image limitée à 0,96 diamètre. Le bas de l'image est au moins au bas du rond, pour que le bas plat du buste ne se voie pas. Tout ce qui est sous le centre du rond est coupé au bord intérieur du contour ; au-dessus, les oreilles dépassent.
- **Réglages par badge** (`badges.json`) : `aligner_bas` (bas de l'image sur le bas du rond, utilisé pour `player-play`, dont le bouton tombait sinon sous le rond), `taille` (facteur d'agrandissement), `yeux` (mesure à la main), `apercu` (couleur de pastille dans l'aperçu).
- **Fichiers** : 256 couleurs sans tramage (écart invisible à taille réelle, fichiers environ 5 fois plus légers).

## Licence

L'outil est sous la licence du dépôt (AGPL-3.0). Les images de Tilou ont été générées par l'utilisateur, comme les autres images de Tilou du site.
