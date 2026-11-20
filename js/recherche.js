// Recherche commune (décision 44) : la barre de recherche montre d'abord les
// séries de « Ma liste » qui correspondent au texte tapé, tout de suite, puis
// les résultats de TMDB (décision 18, option C).
//
// Ce fichier ne parle ni à Supabase ni à TMDB et n'utilise pas Vue : il ne
// contient que des calculs sur la liste déjà chargée. On peut donc le tester
// seul (sous Node).
//
// Règles (choix de Claude, à revoir après des tests) :
// - majuscules, accents et ponctuation ignorés : « l’attaque » trouve
//   « L'Attaque des Titans », « one punch » trouve « One-Punch Man » ;
// - chaque mot tapé doit être le DÉBUT d'un mot du titre, dans n'importe quel
//   ordre : « titans att » trouve « L'Attaque des Titans », mais « unch » ne
//   trouve pas « One-Punch Man » (sinon, deux lettres trouveraient presque
//   toute la liste) ;
// - seul le titre affiché (titre français de TMDB) est regardé ;
// - l'ordre est celui de la liste : activité la plus récente d'abord.

// Lettres liées que la décomposition Unicode ne sépare pas.
const LIGATURES = { 'œ': 'oe', 'æ': 'ae', 'ß': 'ss' };

/**
 * Texte « simplifié » pour comparer : minuscules, sans accents, et tout ce
 * qui n'est ni lettre ni chiffre remplacé par une espace.
 * Exemple : « L’Œuvre — Saison 2 » → « l oeuvre saison 2 ».
 */
export function normaliser(texte) {
  return String(texte ?? '')
    .toLowerCase()
    .replace(/[œæß]/g, (c) => LIGATURES[c])
    .normalize('NFD')                  // « é » devient « e » + accent séparé…
    .replace(/\p{M}/gu, '')            // … et l'accent séparé est retiré
    .replace(/[^\p{L}\p{N}]+/gu, ' ')  // ponctuation, tirets, apostrophes → espace
    .trim();
}

/** Mots d'un texte simplifié : « One-Punch Man » → ['one', 'punch', 'man']. */
export function mots(texte) {
  const simplifie = normaliser(texte);
  return simplifie ? simplifie.split(' ') : [];
}

/** Vrai si chaque mot cherché est le début d'un mot du titre. */
export function correspond(titre, motsCherches) {
  if (motsCherches.length === 0) return false;
  const motsTitre = mots(titre);
  return motsCherches.every((m) => motsTitre.some((t) => t.startsWith(m)));
}

/**
 * Séries de la liste (lignes de la vue « ma_liste ») dont le titre
 * correspond au texte tapé, dans l'ordre de la liste.
 */
export function chercherDansListe(liste, texte) {
  const cherches = mots(texte);
  if (cherches.length === 0) return [];
  return liste.filter((s) => correspond(s.titre, cherches));
}

/**
 * Résultats TMDB sans les séries déjà montrées dans le bloc « Dans ma liste ».
 * Une série suivie que TMDB trouve mais pas notre recherche (par exemple
 * grâce à son titre original) reste dans les résultats TMDB, avec la mention
 * « ✓ Dans ma liste » : elle n'est ainsi jamais cachée.
 */
export function autresResultats(resultats, dejaMontrees) {
  const ids = new Set(dejaMontrees.map((s) => s.tmdb_id));
  return resultats.filter((r) => !ids.has(r.tmdbId));
}
