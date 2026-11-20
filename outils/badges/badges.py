"""Outil de fabrication des images de badges de Canapilou (décision 47).

Usage (depuis n'importe quel dossier) :
    python outils/badges/badges.py decouper PLANCHE.png [--sortie DOSSIER] [--nom NOM] [--une]
    python outils/badges/badges.py yeux SOURCE.png
    python outils/badges/badges.py fabriquer [CODE ...]
    python outils/badges/badges.py apercu [--sortie FICHIER.png]

Voir README.md (même dossier) pour la marche à suivre complète.
Prérequis : Python 3, Pillow (9.1 ou plus récent), NumPy, SciPy.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFont
from scipy import ndimage

DOSSIER = Path(__file__).resolve().parent          # outils/badges/
RACINE = DOSSIER.parents[1]                         # racine du dépôt
IMAGES_SITE = RACINE / 'img' / 'badges'             # où le site lit les images
CONFIG = DOSSIER / 'badges.json'

# --- Géométrie des images (doit suivre css/style.css, .badge-pastille) -------
# Rond de 64 px CSS (4 rem) avec un contour de 2 px, et 24 px CSS (1,5 rem)
# au-dessus pour les oreilles qui dépassent. Images à 3 pixels par pixel CSS.
ECH = 3               # pixels de l'image par pixel CSS
D_CSS = 64            # diamètre du rond
HAUT_CSS = 24         # place au-dessus du rond
BORD_CSS = 2          # épaisseur du contour du rond
SUR = 4               # suréchantillonnage pour des bords lisses

# --- Cadrage de Tilou (choix de Claude, à revoir après des tests) -----------
ECART_YEUX = 0.40     # écart entre les yeux, en fraction du diamètre
Y_YEUX = 0.57         # hauteur des yeux sous le haut du rond, en fraction du diamètre
LARGEUR_MAX = 0.96    # largeur maximale de l'image, en fraction du diamètre

# --- Couleurs des pastilles, copiées de css/style.css (pour l'aperçu) --------
PASTILLES = {
    'bronze': ('#d9a06f', '#a5652f'), 'argent': ('#c9cfd8', '#7d8592'),
    'or': ('#ecc246', '#9a7400'), 'platine': ('#a9dbe3', '#3c8693'),
    'defi': ('#f5b8d0', '#c2185b'),
}
FONDS = {'clair': ('#ffffff', '#1d2330', '#ffffff', '#d9dde5'),     # carte, texte, pastille et contour « non obtenu »
         'sombre': ('#1e232b', '#e8ebf0', '#1e232b', '#333a45')}


# ============================================================================
# Détourage et découpe
# ============================================================================

def detourer(rgb):
    """Retire le fond blanc relié aux bords ; adoucit les bords d'après la
    couleur du contour (évite un halo blanc). Les blancs enfermés (yeux,
    dents, cadran…) sont gardés. Renvoie une image RGBA."""
    a = rgb.astype(float)
    mini = a.min(axis=2)
    presque_blanc = mini > 225
    lab, _ = ndimage.label(presque_blanc)
    bords = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    fond = np.isin(lab, list(bords))
    alpha = np.where(fond, 0.0, 1.0)
    # bande de bord : pixels non-fond voisins du fond -> transparence partielle
    bande = ndimage.binary_dilation(fond, iterations=2) & ~fond
    sombre = bande & (mini < 120)
    c = np.median(a[sombre], axis=0) if sombre.any() else np.array([120, 16, 67.0])
    lum = a.mean(axis=2)
    t = np.clip((255 - lum) / (255 - c.mean()), 0, 1)
    alpha = np.where(bande, np.minimum(alpha, t), alpha)
    out = a.copy()
    m = bande & (t < 0.999)
    k = np.where(t > 0.02, t, 1)[..., None]
    pur = (a - (1 - k) * 255) / k
    out[m] = np.clip(pur[m], 0, 255)
    rgba = np.dstack([out, alpha * 255]).round().astype(np.uint8)
    return Image.fromarray(rgba, 'RGBA')


def _zones(vrais):
    """Suites de valeurs vraies consécutives : [(début, fin), …]."""
    z, d = [], None
    for i, v in enumerate(vrais):
        if v and d is None:
            d = i
        if not v and d is not None:
            z.append((d, i - 1)); d = None
    if d is not None:
        z.append((d, len(vrais) - 1))
    return z


def _morceaux(occupe, debut, fin, axe):
    """Coupe [debut, fin] le long des bandes blanches (3 px ou plus) de l'axe donné."""
    bloc = occupe[debut:fin + 1] if axe == 0 else occupe[:, debut:fin + 1]
    vide = ~bloc.any(axis=1 - axe)
    trous = [z for z in _zones(vide) if z[1] - z[0] >= 3]
    bornes, prec = [], 0
    for t0, t1 in trous:
        if t0 > prec:
            bornes.append((debut + prec, debut + t0 - 1))
        prec = t1 + 1
    if prec <= fin - debut:
        bornes.append((debut + prec, fin))
    return bornes


def decouper(chemin, sortie, nom, une_seule):
    a = np.asarray(Image.open(chemin).convert('RGB'))
    H, W = a.shape[:2]
    occupe = a.min(axis=2) < 225
    if une_seule:
        rangees = [(0, H - 1)]
    else:
        rangees = _morceaux(occupe, 0, H - 1, 0)
    pieces = []
    for y0, y1 in rangees:
        colonnes = [(0, W - 1)] if une_seule else _morceaux(occupe[y0:y1 + 1], 0, W - 1, 1)
        for x0, x1 in colonnes:
            ys, xs = np.nonzero(occupe[y0:y1 + 1, x0:x1 + 1])
            if len(ys) == 0:
                continue
            b = (y0 + ys.min(), x0 + xs.min(), y0 + ys.max(), x0 + xs.max())
            if b[2] - b[0] < 50 or b[3] - b[1] < 50:
                print(f'  petit morceau ignoré ({b[3] - b[1] + 1} × {b[2] - b[0] + 1} px) en x={b[1]}, y={b[0]}')
                continue
            pieces.append(b)
    sortie.mkdir(parents=True, exist_ok=True)
    print(f'{chemin} : {W} × {H} px, {len(pieces)} figure(s)')
    for i, (y0, x0, y1, x1) in enumerate(pieces, 1):
        morceau = np.pad(a[y0:y1 + 1, x0:x1 + 1], ((4, 4), (4, 4), (0, 0)), constant_values=255)
        rgba = detourer(morceau)
        rgba = rgba.crop(rgba.getbbox())
        f = sortie / f'{nom}-{i}.png'
        rgba.save(f, optimize=True)
        # Le bas du buste touche souvent le bord de l'image : normal, pas signalé.
        touche = [cote for cote, oui in [('gauche', x0 <= 1), ('droite', x1 >= W - 2),
                                         ('haut', y0 <= 1)] if oui]
        alerte = f'  touche le bord : {", ".join(touche)} (élément peut-être coupé)' if touche else ''
        print(f'  {f.name} : {rgba.width} × {rgba.height} px{alerte}')


# ============================================================================
# Repérage des yeux (étalon de taille et de position de Tilou)
# ============================================================================

def yeux(chemin):
    """Cherche les deux blancs des yeux : renvoie (x, y, écart) en pixels de
    l'image, ou None. Échoue si un œil est caché (renseigner alors « yeux »
    à la main dans badges.json)."""
    a = np.asarray(Image.open(chemin).convert('RGBA')).astype(int)
    blanc = (a[..., 3] > 250) & (a[..., :3].min(axis=2) > 225)
    lab, _ = ndimage.label(blanc)
    cands = []
    for i, sl in enumerate(ndimage.find_objects(lab), 1):
        h = sl[0].stop - sl[0].start; w = sl[1].stop - sl[1].start
        surf = (lab[sl] == i).sum()
        if surf < 0.002 * a.shape[0] * a.shape[1]:
            continue
        if not (0.2 < surf / (h * w) and 0.6 < h / w < 2.2):
            continue
        cy, cx = ndimage.center_of_mass(lab == i)
        cands.append((surf, cx, cy, w, h))
    meilleur = None
    for i in range(len(cands)):
        for j in range(i + 1, len(cands)):
            s1, x1, y1, w1, h1 = cands[i]; s2, x2, y2, w2, h2 = cands[j]
            if abs(y1 - y2) > 0.5 * max(h1, h2) or max(s1, s2) / min(s1, s2) > 1.8:
                continue
            ecart = abs(x1 - x2)
            if ecart < 1.2 * max(w1, w2):
                continue
            score = w1 * h1 + w2 * h2
            if meilleur is None or score > meilleur[0]:
                meilleur = (score, (x1 + x2) / 2, (y1 + y2) / 2, ecart)
    return None if meilleur is None else meilleur[1:]


# ============================================================================
# Fabrication des images du site
# ============================================================================

def fabriquer_un(code, reglage):
    im = Image.open(DOSSIER / reglage['source']).convert('RGBA')
    W, H = im.size
    if 'yeux' in reglage:                       # mesurés à la main, en fractions + écart en px
        fx, fy, ecart = reglage['yeux']
        ex, ey = fx * W, fy * H
    else:
        trouve = yeux(DOSSIER / reglage['source'])
        if trouve is None:
            raise SystemExit(f'{code} : yeux introuvables, renseigner "yeux" dans badges.json')
        ex, ey, ecart = trouve
    k = ECH * SUR
    D, haut, bord = D_CSS * k, HAUT_CSS * k, BORD_CSS * k
    Wc, Hc = D, D + haut
    s = ECART_YEUX * D / ecart
    reduit = W * s > LARGEUR_MAX * D
    if reduit:
        s = LARGEUR_MAX * D / W
    s *= reglage.get('taille', 1)
    x = D / 2 - ex * s                                       # yeux centrés
    x = min(max(x, D - W * s), 0) if W * s > D else min(max(x, 0), D - W * s)
    y = haut + Y_YEUX * D - ey * s
    bas = y + H * s
    if bas < haut + D or reglage.get('aligner_bas'):        # bas plat jamais visible
        y += haut + D - bas
    if y < 0:
        print(f'  {code} : oreilles coupées en haut de {-y / k:.1f} px CSS')
    r = im.resize((round(W * s), round(H * s)), Image.LANCZOS)
    calque = Image.new('RGBA', (Wc, Hc), (0, 0, 0, 0))
    calque.alpha_composite(r, (round(x), round(y)))
    # Masque : intérieur du rond + tout ce qui est au-dessus de son centre
    m = Image.new('L', (Wc, Hc), 0)
    dm = ImageDraw.Draw(m)
    dm.ellipse((bord, haut + bord, D - 1 - bord, Hc - 1 - bord), fill=255)
    dm.rectangle((0, 0, Wc, haut + D // 2), fill=255)
    calque.putalpha(ImageChops.multiply(calque.getchannel('A'), m))
    fin = calque.resize((Wc // SUR, Hc // SUR), Image.LANCZOS)
    # 256 couleurs, sans tramage : invisible à taille réelle, fichiers 5 fois plus légers
    fin = fin.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)
    return fin, reduit


def fabriquer(codes):
    config = json.loads(CONFIG.read_text(encoding='utf-8'))['badges']
    for code in codes or config:
        if code not in config:
            raise SystemExit(f'{code} : absent de badges.json')
        fin, reduit = fabriquer_un(code, config[code])
        f = IMAGES_SITE / f'{code}.png'
        fin.save(f, optimize=True)
        print(f'{f.relative_to(RACINE)} : {fin.size[0]} × {fin.size[1]} px, {f.stat().st_size // 1024} Ko'
              + ('  (réduit pour tenir en largeur)' if reduit else ''))


# ============================================================================
# Aperçu : images du site posées sur leurs pastilles, comme le fait le CSS
# ============================================================================

def pastille(img, fond, contour, gris=False):
    D, haut, bord = D_CSS * ECH, HAUT_CSS * ECH, BORD_CSS * ECH
    c = Image.new('RGBA', (D * 4, (D + haut) * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(c)
    d.ellipse((0, haut * 4, D * 4 - 1, (D + haut) * 4 - 1), fill=contour)
    d.ellipse((bord * 4, (haut + bord) * 4, (D - bord) * 4 - 1, (D + haut - bord) * 4 - 1), fill=fond)
    c = c.resize((D, D + haut), Image.LANCZOS)
    if gris:                                     # comme .badge-manquant : gris et opacité 0,45
        g = img.convert('LA').convert('RGBA')
        g.putalpha(g.getchannel('A').point(lambda v: v * 45 // 100))
        img = g
    c.alpha_composite(img)
    return c


def apercu(sortie):
    config = json.loads(CONFIG.read_text(encoding='utf-8'))['badges']
    codes = list(config)
    D, haut = D_CSS * ECH, HAUT_CSS * ECH
    marge = 12 * ECH
    police = None
    for nom_police in ('DejaVuSans.ttf', 'arial.ttf'):     # Linux, puis Windows
        try:
            police = ImageFont.truetype(nom_police, 11 * ECH)
            break
        except OSError:
            pass
    police = police or ImageFont.load_default()
    cases = len(codes) + 1
    pl = Image.new('RGB', ((D + marge) * cases + marge, (D + haut + 35 * ECH) * 2), 'white')
    dr = ImageDraw.Draw(pl)
    for j, (nom, (carte, texte, gris_fond, gris_contour)) in enumerate(FONDS.items()):
        Y = j * (D + haut + 35 * ECH)
        dr.rectangle((0, Y, pl.width, Y + D + haut + 35 * ECH), fill=carte)
        for i, code in enumerate(codes + [codes[0]]):
            img = Image.open(IMAGES_SITE / f'{code}.png').convert('RGBA')
            dernier = i == len(codes)
            if dernier:
                b = pastille(img, gris_fond, gris_contour, gris=True)
            else:
                b = pastille(img, *PASTILLES[config[code].get('apercu', 'defi')])
            X = marge + i * (D + marge)
            pl.paste(b, (X, Y + 10 * ECH), b)
            dr.text((X, Y + D + haut + 14 * ECH), 'non obtenu' if dernier else code, font=police, fill=texte)
    pl.save(sortie)
    print(f'Aperçu : {sortie}')


# ============================================================================

def main():
    p = argparse.ArgumentParser(description="Images de badges de Canapilou (voir README.md).")
    sp = p.add_subparsers(dest='commande', required=True)
    d = sp.add_parser('decouper', help='découper une planche de figures et les détourer')
    d.add_argument('planche', type=Path)
    d.add_argument('--sortie', type=Path, default=Path('.'), help='dossier de sortie (défaut : dossier courant)')
    d.add_argument('--nom', default=None, help='début du nom des fichiers (défaut : nom de la planche)')
    d.add_argument('--une', action='store_true', help="l'image ne contient qu'une figure (pas de découpe)")
    y = sp.add_parser('yeux', help='afficher les yeux repérés dans une image détourée')
    y.add_argument('source', type=Path)
    f = sp.add_parser('fabriquer', help='fabriquer les images du site (img/badges/)')
    f.add_argument('codes', nargs='*', help='seulement ces badges (défaut : tous)')
    a = sp.add_parser('apercu', help="planche d'aperçu des images du site sur leurs pastilles")
    a.add_argument('--sortie', type=Path, default=DOSSIER / 'apercu.png')
    args = p.parse_args()
    if args.commande == 'decouper':
        decouper(args.planche, args.sortie, args.nom or args.planche.stem, args.une)
    elif args.commande == 'yeux':
        im = Image.open(args.source)
        r = yeux(args.source)
        if r is None:
            print('Yeux introuvables (un œil caché ?) : les mesurer à la main.')
        else:
            x, yy, e = r
            print(f'centre des yeux : x = {x / im.width:.2f}, y = {yy / im.height:.2f} '
                  f'(en fraction de l\'image), écart = {e:.0f} px')
    elif args.commande == 'fabriquer':
        fabriquer(args.codes)
    else:
        apercu(args.sortie)


if __name__ == '__main__':
    sys.exit(main())
