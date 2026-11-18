// Import d'une sauvegarde (décision 40).
//
// Ce fichier lit et vérifie un fichier de sauvegarde (celui que produit
// « Exporter mes données »), puis calcule ce qu'il faut ajouter à la liste.
// Il ne parle ni à Supabase ni à TMDB : les écritures sont faites par
// app.js avec les fonctions de base.js. On peut donc le tester seul.

/** Taille maximale d'un fichier de sauvegarde (choix de Claude, à revoir). */
export const TAILLE_MAX = 10 * 1024 * 1024; // 10 Mo

const STATUTS = ['a_voir', 'en_cours', 'a_jour', 'termine', 'abandonne', 'plus_tard'];
const CATEGORIES = ['serie', 'anime'];

/**
 * Lit le texte d'un fichier de sauvegarde. Renvoie
 * { exporteLe, series, ignorees } où chaque série vaut
 * { tmdbId, titre, statutManuel, categorieManuelle, ajouteLe, episodes: [{ saison, episode, vuLe }] }
 * et « ignorees » liste les titres des séries sans identifiant TMDB.
 * Lève une erreur en français si le fichier n'est pas une sauvegarde valable.
 */
export function lireSauvegarde(texte) {
  let donnees;
  try {
    donnees = JSON.parse(texte);
  } catch {
    throw new Error("Ce fichier n'est pas lisible : ce n'est pas une sauvegarde du site.");
  }
  if (!donnees || donnees.format !== 'suivi-series' || !Array.isArray(donnees.series)) {
    throw new Error("Ce fichier n'est pas une sauvegarde du site.");
  }
  if (donnees.version !== 1) {
    throw new Error(`Cette sauvegarde est d'une version inconnue (${donnees.version}).`);
  }

  const parTmdb = new Map();
  const ignorees = [];
  for (const s of donnees.series) {
    if (!s || typeof s !== 'object') continue;
    const tmdbId = s.tmdb_id;
    if (!Number.isInteger(tmdbId) || tmdbId <= 0) {
      ignorees.push(texteOuDefaut(s.titre));
      continue;
    }
    const episodes = Array.isArray(s.episodes_vus) ? s.episodes_vus : [];
    const serie = parTmdb.get(tmdbId) ?? {
      tmdbId,
      titre: texteOuDefaut(s.titre),
      statutManuel: STATUTS.includes(s.statut_manuel) ? s.statut_manuel : null,
      categorieManuelle: CATEGORIES.includes(s.categorie_manuelle) ? s.categorie_manuelle : null,
      ajouteLe: dateOuNull(s.ajoute_le),
      episodes: new Map(), // « saison-episode » → { saison, episode, vuLe }
    };
    for (const e of episodes) {
      if (!e || !Number.isInteger(e.saison) || !Number.isInteger(e.episode)) continue;
      if (e.saison < 0 || e.episode < 0) continue;
      const cle = `${e.saison}-${e.episode}`;
      if (!serie.episodes.has(cle)) {
        serie.episodes.set(cle, { saison: e.saison, episode: e.episode, vuLe: dateOuNull(e.vu_le) });
      }
    }
    parTmdb.set(tmdbId, serie); // une série présente deux fois est réunie en une seule
  }

  const series = [...parTmdb.values()].map((s) => ({ ...s, episodes: [...s.episodes.values()] }));
  return { exporteLe: dateOuNull(donnees.exporte_le), series, ignorees };
}

/**
 * Ce qu'un import ajoutera, selon le mode :
 * - 'ajout' : les séries absentes de la liste sont ajoutées entières ; pour
 *   les séries déjà suivies, seuls les épisodes pas encore cochés sont
 *   ajoutés (leurs statut et catégorie actuels ne changent pas) ;
 * - 'remplacement' : la liste actuelle sera vidée, puis toute la sauvegarde
 *   sera remise.
 * « actuelles » : la liste actuelle, telle que la renvoie lireDonneesPourExport()
 * (tmdb_id, statut_manuel, episodes_vus).
 * Renvoie { nouvelles, completees, nbEpisodes, nbRetirees } ; chaque série
 * complétée vaut { tmdbId, titre, statutActuel, episodes }.
 */
export function planifierImport(sauvegarde, actuelles, mode) {
  if (mode === 'remplacement') {
    return {
      nouvelles: sauvegarde.series,
      completees: [],
      nbEpisodes: compterEpisodes(sauvegarde.series),
      nbRetirees: actuelles.length,
    };
  }
  const parTmdb = new Map(actuelles.filter((a) => a.tmdb_id != null).map((a) => [a.tmdb_id, a]));
  const nouvelles = [];
  const completees = [];
  for (const s of sauvegarde.series) {
    const actuelle = parTmdb.get(s.tmdbId);
    if (!actuelle) {
      nouvelles.push(s);
      continue;
    }
    const dejaVus = new Set((actuelle.episodes_vus ?? []).map((e) => `${e.saison}-${e.episode}`));
    const manquants = s.episodes.filter((e) => !dejaVus.has(`${e.saison}-${e.episode}`));
    if (manquants.length > 0) {
      completees.push({ tmdbId: s.tmdbId, titre: s.titre, statutActuel: actuelle.statut_manuel ?? null, episodes: manquants });
    }
  }
  return { nouvelles, completees, nbEpisodes: compterEpisodes(nouvelles) + compterEpisodes(completees), nbRetirees: 0 };
}

/** Nombre total d'épisodes cochés d'une liste de séries. */
export function compterEpisodes(series) {
  return series.reduce((total, s) => total + s.episodes.length, 0);
}

function texteOuDefaut(texte) {
  return typeof texte === 'string' && texte.trim() ? texte.trim() : '(sans titre)';
}

// Date au format ISO (« 2026-11-19T10:00:00.000Z »), ou null si absente ou illisible.
function dateOuNull(valeur) {
  if (typeof valeur !== 'string') return null;
  const t = Date.parse(valeur);
  return Number.isNaN(t) ? null : new Date(t).toISOString();
}
