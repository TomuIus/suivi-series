// Tout ce qui concerne TMDB passe par ce fichier.
//
// Le site ne parle jamais directement à l'API TMDB : il passe par notre
// fonction serveur « tmdb » (supabase/functions/tmdb/index.ts), qui garde le
// jeton TMDB secret. Seules les images (affiches) sont chargées directement
// depuis les serveurs d'images de TMDB.
//
// Choix de conception : si on change de source un jour (TVmaze…), c'est ce
// fichier et la fonction serveur qu'il faudra réécrire, pas le reste du site.

import { appelerFonction } from './base.js';

const SERVEUR_IMAGES = 'https://image.tmdb.org/t/p/';

/**
 * Recherche des séries et animés par titre (20 résultats au plus).
 * Chaque résultat : { tmdbId, titre, titreOriginal, annee, affiche, resume, pays }.
 */
export async function rechercherSeries(texte) {
  const { resultats } = await appelerFonction('tmdb', { action: 'rechercher', texte });
  return resultats;
}

/**
 * Copie (ou met à jour) la fiche TMDB d'une série dans le catalogue du site.
 * Renvoie { id, titre, resume } ; « id » est l'identifiant interne de la série.
 */
export async function importerSerie(tmdbId) {
  return appelerFonction('tmdb', { action: 'ajouter', tmdbId });
}

/**
 * Aperçu d'une série SANS l'ajouter ni l'enregistrer : { serie, saisons },
 * avec les mêmes noms de champs que dans la base (tmdb_id, titre, …, resume).
 */
export async function apercuSerie(tmdbId) {
  return appelerFonction('tmdb', { action: 'fiche', tmdbId });
}

/** Titres et dates de sortie des épisodes d'une saison : [{ numero, titre, date }]. */
export async function episodesSaison(tmdbId, saison) {
  const { episodes } = await appelerFonction('tmdb', { action: 'saison', tmdbId, saison });
  return episodes;
}

/**
 * Adresse complète d'une affiche à partir du chemin fourni par TMDB.
 * Tailles proposées par TMDB pour les affiches : w92, w154, w185, w342, w500, w780.
 */
export function urlAffiche(chemin, taille = 'w154') {
  return chemin ? `${SERVEUR_IMAGES}${taille}${chemin}` : null;
}
