# Outil des images de Tilou de rang

Fabrique les images de Tilou affichées dans le Profil selon le rang (site et appli Android), à partir des images générées par l'utilisateur. Choix détaillés dans le journal du projet (décisions 108 à 114).

Le site et l'appli n'utilisent pas ce dossier : ils lisent les images déjà fabriquées, `img/rangs/rang-N.png` pour le site et `android/app/src/main/res/drawable-nodpi/tilou_rang_N.png` pour l'appli. Ce sont les mêmes fichiers. N est le numéro du rang (`rang_numero` de la vue `ma_progression`, de 1 à 11).

## Contenu

- `rangs.py` : l'outil (commandes `fabriquer` et `apercu`).
- `rangs.json` : les rangs qui ont une image, avec leur nom, leur niveau et leur image source.
- `sources/` : une image détourée par rang, en pleine taille (images générées par l'utilisateur, découpées et détourées).
- `apercu.png` : planche d'aperçu, créée par la commande `apercu`. Elle n'est pas envoyée sur GitHub (voir `.gitignore`).

## Prérequis

Python 3 avec Pillow (9.1 ou plus récent). Le détourage passe par l'outil des badges, qui demande aussi NumPy et SciPy :

```
python -m pip install pillow numpy scipy
```

Non vérifié sur le PC de l'utilisateur. Claude peut aussi lancer l'outil dans son propre espace de travail.

Les commandes se lancent depuis la racine du dépôt.

## Ajouter le Tilou d'un rang

1. **Générer l'image** en joignant l'image de Tilou au pop-corn (l'original en grand, sinon `img/tilou-popcorn.png`), avec la consigne :

   > Le lapin rose de l'image jointe (même personnage, mêmes proportions, même style plat, même contour framboise foncé), debout, vu de trois quarts, en entier de la tête aux pieds, même cadrage que l'image jointe. Fond blanc uni, sans ombre, sans texte, sans logo. Tous les éléments ont un contour foncé et restent proches de lui. Il ne tient pas de pop-corn.

   Puis ajouter l'élément du rang (pour « Sentinelle du pop-corn », retirer la dernière phrase) :

   | N | Niveau | Rang | Élément |
   |---|---|---|---|
   | 1 | 1 | Touriste du salon | appareil photo autour du cou, l'air curieux et émerveillé (fait) |
   | 2 | 3 | Fan du dimanche | pyjama à rayures, chaussons moelleux, mug fumant (fait) |
   | 3 | 5 | Adepte du plaid | enroulé dans un plaid douillet (fait) |
   | 4 | 8 | Fidèle des coussins | serre un gros coussin moelleux dans ses bras, l'air câlin (fait) |
   | 5 | 12 | As de la télécommande | brandit une télécommande, l'air malin (fait) |
   | 6 | 17 | Sentinelle du pop-corn | garde un seau de pop-corn géant |
   | 7 | 25 | Pro du marathon | bandeau de sport et dossard |
   | 8 | 35 | Pilier du canapé | robe de chambre et pantoufles, très à l'aise |
   | 9 | 50 | Institution du salon | veste d'intérieur en velours, tasse de thé, air distingué |
   | 10 | 70 | Légende du canapé | cape de héros qui flotte |
   | 11 | 100 | Canapilou d'or | couronne et trophée d'or en forme de canapé |

2. **Découper et détourer** avec l'outil des badges :

   ```
   python outils/badges/badges.py decouper image.png --sortie essais --une
   ```

   `--une` garde ensemble les éléments séparés par du blanc (vapeur d'un mug, traits autour d'un objet). L'outil signale une figure qui touche le bord : les oreilles touchent souvent le haut de l'image, il manque alors au plus un pixel de contour, invisible en petit.

3. **Ranger l'image** détourée dans `sources/` (par exemple `sources/rang-4-fidele-des-coussins.png`) et l'ajouter à `rangs.json`.

4. **Fabriquer** :

   ```
   python outils/rangs/rangs.py fabriquer 4
   ```

   L'outil recadre Tilou au plus près, le met à 336 pixels de haut (3 pixels par point pour les 112 points du Profil ; largeur selon l'image) et le réduit à 256 couleurs sans tramage. Il écrit la même image pour le site et pour l'appli.

   Ne fabriquer que les nouveaux rangs (en donnant leur numéro) : refabriquer une image existante donne les mêmes pixels, mais un fichier légèrement différent, que Git verrait comme modifié.

5. **Vérifier** avec `python outils/rangs/rangs.py apercu` (planche en clair et en sombre, taille du Profil agrandie deux fois).

6. **Déclarer le rang** comme ayant une image, aux deux endroits :
   - site : `RANGS_AVEC_IMAGE` dans `js/badges.js` (puis augmenter le numéro de version `?v=N` dans `index.html`) ;
   - appli : fonction `imageRang` dans `android/app/src/main/java/ui/profil/EcranProfil.kt` (puis réinstaller l'appli).

   Tant qu'un rang n'est pas déclaré, le Profil montre Tilou au pop-corn.

## Licence

L'outil est sous la licence du dépôt (AGPL-3.0). Les images de Tilou ont été générées par l'utilisateur, comme les autres images de Tilou du site.
