"""Outil de fabrication des images de Tilou de rang de Canapilou (décisions 111 à 114).

Usage (depuis n'importe quel dossier) :
    python outils/rangs/rangs.py fabriquer [NUMERO ...]
    python outils/rangs/rangs.py apercu [--sortie FICHIER.png]

Le découpage et le détourage des images générées se font avec l'outil des
badges (python outils/badges/badges.py decouper IMAGE.png --une) : voir README.md.
Prérequis : Python 3, Pillow (9.1 ou plus récent).
"""
import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

DOSSIER = Path(__file__).resolve().parent          # outils/rangs/
RACINE = DOSSIER.parents[1]                         # racine du dépôt
IMAGES_SITE = RACINE / 'img' / 'rangs'              # où le site lit les images
IMAGES_APPLI = RACINE / 'android' / 'app' / 'src' / 'main' / 'res' / 'drawable-nodpi'
CONFIG = DOSSIER / 'rangs.json'

# Hauteur affichée sur le Profil : 112 points (7 rem sur le site, 112 dp dans
# l'appli), largeur selon l'image. Images à 3 pixels par point, comme les
# badges (choix de Claude, à revoir après des tests).
HAUTEUR = 112 * 3


def fabriquer_un(numero, reglage):
    """Image du rang : Tilou recadré au plus près, mis à la hauteur du Profil,
    réduit à 256 couleurs sans tramage (comme les badges : écart invisible à
    taille réelle, fichier environ quatre fois plus léger)."""
    im = Image.open(DOSSIER / reglage['source']).convert('RGBA')
    im = im.crop(im.getbbox())                       # au plus près de Tilou
    largeur = round(im.width * HAUTEUR / im.height)
    reduit = im.resize((largeur, HAUTEUR), Image.LANCZOS)
    return reduit.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)


def fabriquer(numeros):
    config = json.loads(CONFIG.read_text(encoding='utf-8'))['rangs']
    for numero in numeros or config:
        if numero not in config:
            sys.exit(f'Rang inconnu dans rangs.json : {numero}')
        image = fabriquer_un(numero, config[numero])
        IMAGES_SITE.mkdir(parents=True, exist_ok=True)
        site = IMAGES_SITE / f'rang-{numero}.png'
        appli = IMAGES_APPLI / f'tilou_rang_{numero}.png'
        image.save(site, optimize=True)
        image.save(appli, optimize=True)
        print(f"rang {numero} ({config[numero]['nom']}) : {image.width} × {image.height} px"
              f" → {site.relative_to(RACINE)} et {appli.relative_to(RACINE)}")
    print('Penser à compléter la liste des rangs qui ont une image : RANGS_AVEC_IMAGE dans'
          ' js/badges.js et imageRang dans android/.../ui/profil/EcranProfil.kt.')


def apercu(sortie):
    """Planche d'aperçu : chaque image du site à la taille du Profil (agrandie
    × 2), sur le fond des cartes du site en clair et en sombre."""
    config = json.loads(CONFIG.read_text(encoding='utf-8'))['rangs']
    E = 2
    h = 112 * E
    themes = [('#ffffff', '#5b6475'), ('#1e232b', '#a3abb8')]   # --carte, --texte-doux
    images = []
    for numero, reglage in config.items():
        chemin = IMAGES_SITE / f'rang-{numero}.png'
        if not chemin.exists():
            sys.exit(f'{chemin.relative_to(RACINE)} manque : lancer d\'abord « fabriquer ».')
        im = Image.open(chemin).convert('RGBA')
        im = im.resize((round(im.width * h / im.height), h), Image.LANCZOS)
        images.append((numero, reglage['nom'], im))
    case = 150 * E
    planche = Image.new('RGB', (case * len(images), 2 * (h + 40 * E)), '#ffffff')
    dessin = ImageDraw.Draw(planche)
    try:
        police = ImageFont.truetype('DejaVuSans.ttf', 10 * E)
    except OSError:
        police = ImageFont.load_default()
    for ligne, (fond, texte) in enumerate(themes):
        y = ligne * (h + 40 * E)
        dessin.rectangle([0, y, planche.width, y + h + 40 * E], fill=fond)
        for i, (numero, nom, im) in enumerate(images):
            planche.paste(im, (i * case + (case - im.width) // 2, y + 10 * E), im)
            dessin.text((i * case + 6 * E, y + h + 18 * E), f'{numero} · {nom}', font=police, fill=texte)
    planche.save(sortie)
    print(f'Aperçu : {sortie}')


def main():
    p = argparse.ArgumentParser(description='Images de Tilou de rang de Canapilou (voir README.md).')
    sp = p.add_subparsers(dest='commande', required=True)
    f = sp.add_parser('fabriquer', help="fabriquer les images du site (img/rangs/) et de l'appli")
    f.add_argument('numeros', nargs='*', help='numéros de rang (tous ceux de rangs.json par défaut)')
    a = sp.add_parser('apercu', help="planche d'aperçu des images du site")
    a.add_argument('--sortie', default=str(DOSSIER / 'apercu.png'))
    args = p.parse_args()
    if args.commande == 'fabriquer':
        fabriquer(args.numeros)
    else:
        apercu(args.sortie)


if __name__ == '__main__':
    main()
