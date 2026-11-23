"""Outil de l'icône de l'appli Android Canapilou (décisions 71 et 72).

Fabrique, à partir de l'image d'origine de Tilou au pop-corn
(tilou-popcorn-source.png), tous les fichiers de l'icône rangés dans
android/app/src/main/res/ :
  - mipmap-<densité>/ic_launcher_tilou.png : premier plan de l'icône
    adaptative (108 × 108 dp, de 108 à 432 px selon la densité) ;
  - drawable/ic_launcher_background.xml : fond uni (couleur FOND) ;
  - mipmap-<densité>/ic_launcher.webp et ic_launcher_round.webp : anciennes
    icônes (48 dp), réservées à Android 7 et avant, jamais utilisées par
    l'appli (minSdk 26), refaites pour ne pas garder l'icône d'Android.
Les deux fichiers mipmap-anydpi/ic_launcher*.xml, qui assemblent fond et
premier plan, sont écrits à la main : l'outil n'y touche pas.

Commandes (depuis la racine du dépôt) :
  python outils/icone/icone.py fabriquer [--sortie DOSSIER]
  python outils/icone/icone.py apercu
"""

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ICI = Path(__file__).resolve().parent
SOURCE = ICI / 'tilou-popcorn-source.png'
SORTIE_PAR_DEFAUT = ICI.parent.parent / 'android' / 'app' / 'src' / 'main' / 'res'

# --- Réglages (valeurs du 23/11/2026, choix de Claude sauf la couleur) ---

FOND = '#C294D4'          # mauve du canapé (img/tilou-canape.png), choisi par l'utilisateur
CONTOUR = (125, 3, 58)    # couleur du contour de Tilou, pour adoucir les bords

# Détourage : un pixel « presque blanc » a toutes ses composantes au-dessus de
# SEUIL_BLANC et peu d'écart entre elles (ECART_GRIS) ; le fond est fait des
# zones presque blanches reliées au bord de l'image.
SEUIL_BLANC = 232
ECART_GRIS = 18

# Mise en place, en dp (1 dp = 4 px à la densité xxxhdpi) : calque de 108 dp,
# dont la documentation Android garantit que les 66 × 66 dp du centre ne sont
# jamais coupés. La partie haute de Tilou (au-dessus de Y_LIMITE dans l'image
# d'origine : oreilles, tête, pop-corn) doit tenir dans un cercle de
# 2 × RAYON_SUR_DP au centre ; plus bas, le buste peut être coupé par la forme
# de l'icône. L'image est coupée assez bas pour que la coupe tombe sous la
# zone visible (bas du carré de 72 dp, plus MARGE_BAS_DP).
RAYON_SUR_DP = 32
Y_LIMITE = 1000
MARGE_BAS_DP = 2

DENSITES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

FOND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Fond de l'icône de l'appli : mauve du canapé de Tilou (#C294D4, couleur
     prise sur l'illustration img/tilou-canape.png du site), décision 72. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="{couleur}"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""


def detourer(chemin):
    """Retire le fond blanc relié aux bords ; bords adoucis d'après le mélange contour / blanc."""
    src = np.asarray(Image.open(chemin).convert('RGB')).astype(np.float32)
    mn, mx = src.min(axis=2), src.max(axis=2)
    presque_blanc = (mn > SEUIL_BLANC) & (mx - mn < ECART_GRIS)
    etiquettes, _ = ndimage.label(presque_blanc)
    bords = np.concatenate([etiquettes[0], etiquettes[-1], etiquettes[:, 0], etiquettes[:, -1]])
    fond = np.isin(etiquettes, list(set(np.unique(bords)) - {0}))
    # Sujet : tout le reste, en gardant la plus grande pièce.
    sujet = ~fond
    et2, n2 = ndimage.label(sujet)
    tailles = ndimage.sum(sujet, et2, range(1, n2 + 1))
    sujet = et2 == (1 + int(np.argmax(tailles)))
    enfermes = int((presque_blanc & ~fond & ~sujet).sum())
    if enfermes:
        print(f'Attention : {enfermes} pixels de fond enfermés dans le sujet (restés opaques).')
    # Bord : transparence estimée sur le canal vert (le contour en a très peu).
    contour = np.array(CONTOUR, dtype=np.float32)
    alpha = np.where(sujet, 1.0, 0.0).astype(np.float32)
    g = src[..., 1]
    a_bord = np.clip((255 - g) / (255 - contour[1]), 0, 1)
    bande = sujet & ndimage.binary_dilation(fond, iterations=2)
    alpha[bande] = a_bord[bande]
    frange = fond & ndimage.binary_dilation(sujet, iterations=1)
    alpha[frange] = a_bord[frange]
    rgb = src.copy()
    rgb[bande | frange] = contour
    out = np.dstack([rgb, alpha * 255]).round().clip(0, 255).astype(np.uint8)
    return Image.fromarray(out, 'RGBA')


def mise_en_place(im):
    """Échelle et position (à 4 px par dp) qui font tenir le haut de Tilou dans le cercle sûr."""
    taille, centre, rayon = 108 * 4, 108 * 4 / 2, RAYON_SUR_DP * 4
    a = np.asarray(im)[..., 3]
    ys, xs = np.nonzero(a > 40)
    haut = ys < Y_LIMITE
    pts_y, pts_x = ys[haut], xs[haut]
    cx = (pts_x.min() + pts_x.max()) / 2
    y_haut = pts_y.min()

    def tient(s, dy):
        x = (pts_x - cx) * s + centre
        y = (pts_y - y_haut) * s + dy
        return np.all((x - centre) ** 2 + (y - centre) ** 2 <= rayon ** 2)

    trouve = None
    for s200 in range(10, 80):  # échelles de 0,05 à 0,395, par pas de 0,005
        s = s200 / 200
        for dy in np.arange(centre - rayon, centre, 1.0):
            if tient(s, dy):
                trouve = (s, float(dy))
                break
    s, dy = trouve
    bas_visible = (54 + 36) * 4
    y_coupe = int(y_haut + (bas_visible + MARGE_BAS_DP * 4 - dy) / s)
    return {'echelle': s, 'haut': dy, 'cx': float(cx), 'y_haut': int(y_haut), 'y_coupe': y_coupe}


def premier_plan(im, m, densite):
    """Premier plan de l'icône (108 dp) à une densité donnée, refait depuis l'image détourée."""
    t = round(108 * densite)
    s = m['echelle'] * densite / 4
    morceau = im.crop((0, 0, im.width, min(im.height, m['y_coupe'])))
    r = morceau.resize((round(morceau.width * s), round(morceau.height * s)), Image.LANCZOS)
    fg = Image.new('RGBA', (t, t), (0, 0, 0, 0))
    fg.alpha_composite(r, (round(t / 2 - m['cx'] * s), round(m['haut'] * densite / 4 - m['y_haut'] * s)))
    return fg


def masque(forme, cote):
    """Masque rond ou carré arrondi, dessiné 4 fois plus grand puis réduit (bords lisses)."""
    m = Image.new('L', (cote * 4, cote * 4), 0)
    d = ImageDraw.Draw(m)
    if forme == 'rond':
        d.ellipse((0, 0, cote * 4 - 1, cote * 4 - 1), fill=255)
    else:
        d.rounded_rectangle((0, 0, cote * 4 - 1, cote * 4 - 1), radius=cote, fill=255)
    return m.resize((cote, cote), Image.LANCZOS)


def zone_visible(fg, densite, cote):
    """Fond + premier plan, réduits à la zone visible (72 dp au centre des 108 dp)."""
    t = fg.width
    calque = Image.new('RGBA', (t, t), FOND)
    calque.alpha_composite(fg)
    marge = round(18 * densite)
    return calque.crop((marge, marge, t - marge, t - marge)).resize((cote, cote), Image.LANCZOS)


def fabriquer(sortie):
    im = detourer(SOURCE)
    m = mise_en_place(im)
    print('Mise en place :', json.dumps(m))
    for nom, d in DENSITES.items():
        dossier = sortie / f'mipmap-{nom}'
        dossier.mkdir(parents=True, exist_ok=True)
        fg = premier_plan(im, m, d)
        fg.save(dossier / 'ic_launcher_tilou.png', optimize=True)
        cote = round(48 * d)
        v = zone_visible(fg, d, cote)
        for fichier, forme in [('ic_launcher.webp', 'carre'), ('ic_launcher_round.webp', 'rond')]:
            out = Image.new('RGBA', (cote, cote), (0, 0, 0, 0))
            out.paste(v, (0, 0), masque(forme, cote))
            out.save(dossier / fichier, lossless=True)
        print(f'{nom} : premier plan {fg.width} px, anciennes icônes {cote} px')
    (sortie / 'drawable').mkdir(parents=True, exist_ok=True)
    with open(sortie / 'drawable' / 'ic_launcher_background.xml', 'w', encoding='utf-8', newline='\n') as f:
        f.write(FOND_XML.replace('{couleur}', FOND))
    print('Fichiers écrits dans', sortie)


def apercu():
    """Planche d'aperçu (outils/icone/apercu.png) : formes de masque, fonds d'écran clair et sombre, petites tailles."""
    im = detourer(SOURCE)
    m = mise_en_place(im)
    fg = premier_plan(im, m, 4)
    page = Image.new('RGB', (700, 2 * 300 + 20), '#888888')
    for ligne, fond_ecran in enumerate(['#EDEFF3', '#202124']):
        y0 = 10 + ligne * 300
        page.paste(Image.new('RGB', (page.width - 20, 280), fond_ecran), (10, y0))
        v = zone_visible(fg, 4, 200)
        for i, forme in enumerate(['rond', 'carre']):
            page.paste(v, (30 + i * 220, y0 + 20), masque(forme, 200))
        for j, cote in enumerate([48, 36, 24]):
            page.paste(zone_visible(fg, 4, cote), (480 + j * 70, y0 + 20), masque('rond', cote))
    page.save(ICI / 'apercu.png')
    print('Aperçu :', ICI / 'apercu.png')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description="Outil de l'icône de l'appli Android Canapilou.")
    sous = p.add_subparsers(dest='commande', required=True)
    f = sous.add_parser('fabriquer', help="refait tous les fichiers de l'icône")
    f.add_argument('--sortie', type=Path, default=SORTIE_PAR_DEFAUT,
                   help='dossier res/ de destination (par défaut : celui de l’appli)')
    sous.add_parser('apercu', help="planche d'aperçu (outils/icone/apercu.png)")
    a = p.parse_args()
    if a.commande == 'fabriquer':
        fabriquer(a.sortie)
    else:
        apercu()
