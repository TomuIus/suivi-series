// Tout ce qui parle à Supabase passe par ce fichier.
//
// Choix de conception : si on change d'outil un jour, seul ce fichier sera à
// réécrire ; le reste du site n'appelle que les fonctions exportées ici.

import { createClient } from '@supabase/supabase-js';
import { SUPABASE_URL, SUPABASE_CLE_PUBLIQUE } from './config.js';

// Le « client » Supabase : l'objet qui envoie les requêtes au serveur.
// Il garde aussi la session (vous restez connecté d'une visite à l'autre).
const supabase = createClient(SUPABASE_URL, SUPABASE_CLE_PUBLIQUE);


/** Renvoie l'utilisateur actuellement connecté, ou null s'il n'y en a pas. */
export async function utilisateurConnecte() {
  const { data, error } = await supabase.auth.getSession();
  if (error) throw new Error(traduireErreur(error));
  return data.session?.user ?? null;
}

/**
 * Appelle `rappel(utilisateur)` à chaque connexion ou déconnexion
 * (y compris depuis un autre onglet). Renvoie une fonction qui arrête l'écoute.
 */
export function surChangementDeConnexion(rappel) {
  const { data } = supabase.auth.onAuthStateChange((_evenement, session) => {
    rappel(session?.user ?? null);
  });
  return () => data.subscription.unsubscribe();
}

/** Connexion par e-mail et mot de passe. En cas d'échec, lève une erreur en français. */
export async function seConnecter(email, motDePasse) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password: motDePasse,
  });
  if (error) throw new Error(traduireErreur(error));
  return data.user;
}

/**
 * Enregistre le nom d'utilisateur (affiché à la place de l'e-mail) dans les
 * informations du compte Supabase (« user_metadata »), sans table à créer.
 * null ou '' = plus de nom (l'e-mail est de nouveau affiché).
 * Renvoie l'utilisateur mis à jour.
 */
export async function changerNomUtilisateur(nom) {
  const { data, error } = await supabase.auth.updateUser({ data: { nom_utilisateur: nom || null } });
  if (error) throw new Error(traduireErreur(error));
  return data.user;
}

/** Déconnexion. */
export async function seDeconnecter() {
  const { error } = await supabase.auth.signOut();
  if (error) throw new Error(traduireErreur(error));
}


// --- Fonctions serveur (Edge Functions) ---

/**
 * Appelle une fonction serveur Supabase et renvoie sa réponse (déjà décodée).
 * supabase-js ajoute tout seul le jeton de connexion de l'utilisateur.
 * En cas d'échec, lève une erreur portant le message en français renvoyé
 * par la fonction (champ « erreur »), ou un message générique.
 */
export async function appelerFonction(nom, corps) {
  const { data, error } = await supabase.functions.invoke(nom, { body: corps });
  if (!error) return data;

  // Quand la fonction répond avec un code d'erreur, supabase-js place la
  // réponse brute dans error.context : on y lit notre message.
  let message = '';
  if (typeof error.context?.json === 'function') {
    try {
      message = (await error.context.json())?.erreur ?? '';
    } catch {
      // Réponse illisible : on garde le message générique ci-dessous.
    }
  }
  throw new Error(message || 'Le serveur ne répond pas. Réessayez dans un instant.');
}


// --- Ma liste ---

/**
 * Renvoie les séries suivies par l'utilisateur connecté, de l'activité la
 * plus récente à la plus ancienne. Lit la vue « ma_liste » de la base, qui
 * calcule pour chaque série : statut, épisodes diffusés et vus, prochain
 * épisode. Les règles RLS garantissent qu'on ne reçoit que ses propres suivis.
 */
export async function listerSuivis() {
  const { data, error } = await supabase
    .from('ma_liste')
    .select(
      'serie_id, tmdb_id, titre, affiche_chemin, statut_diffusion, statut_manuel, statut, ' +
      'nb_diffuses, nb_vus, prochaine_saison, prochain_episode, derniere_activite, ' +
      'categorie_manuelle, anime_detecte, est_anime, ' +
      'minutes_vues, nb_vus_sans_duree, durees_a_calculer, nb_speciaux_vus, ' +
      'fiche_a_rafraichir'
    )
    .order('derniere_activite', { ascending: false });
  if (error) throw new Error(traduireErreur(error));
  return data;
}

/**
 * Ajoute une série du catalogue (identifiant interne) à la liste de
 * l'utilisateur connecté. Avec { plusTard: true }, elle va directement dans
 * « Plus tard » (décision 26). Renvoie false si elle y était déjà (son
 * statut n'est alors pas modifié).
 */
export async function suivreSerie(serieId, { plusTard = false } = {}) {
  // utilisateur_id n'est pas envoyé : la base le remplit avec auth.uid().
  const ligne = { serie_id: serieId };
  if (plusTard) ligne.statut_manuel = 'plus_tard';
  const { error } = await supabase.from('suivis').insert(ligne);
  if (!error) return true;
  if (error.code === '23505') return false; // 23505 = doublon : déjà dans la liste
  throw new Error(traduireErreur(error));
}


// --- Épisodes vus ---

/** Coche un épisode comme vu. S'il l'était déjà, ce n'est pas une erreur. */
export async function marquerVu(serieId, saison, episode) {
  const { error } = await supabase
    .from('episodes_vus')
    .insert({ serie_id: serieId, saison, episode });
  if (error && error.code !== '23505') throw new Error(traduireErreur(error));
}

/**
 * Coche plusieurs épisodes d'un coup (liste de { saison, episode }).
 * Les épisodes déjà cochés sont simplement ignorés (« ignoreDuplicates »).
 */
export async function marquerVus(serieId, episodes) {
  if (episodes.length === 0) return;
  const lignes = episodes.map(({ saison, episode }) => ({ serie_id: serieId, saison, episode }));
  const { error } = await supabase
    .from('episodes_vus')
    .upsert(lignes, { onConflict: 'utilisateur_id,serie_id,saison,episode', ignoreDuplicates: true });
  if (error) throw new Error(traduireErreur(error));
}

/** Décoche un épisode (les règles RLS limitent la suppression à ses propres épisodes). */
export async function annulerVu(serieId, saison, episode) {
  const { error } = await supabase
    .from('episodes_vus')
    .delete()
    .eq('serie_id', serieId)
    .eq('saison', saison)
    .eq('episode', episode);
  if (error) throw new Error(traduireErreur(error));
}

/**
 * Décoche plusieurs épisodes d'un coup (liste de { saison, episode }), par
 * exemple pour annuler un « tout cocher » (décision 36). Les épisodes qui se
 * suivent dans une même saison partent ensemble : une requête « saison 1,
 * épisodes 1 à 12 » plutôt que douze. Les autres épisodes ne sont pas touchés.
 */
export async function annulerVus(serieId, episodes) {
  const suites = suitesConsecutives(episodes);
  // 10 requêtes à la fois au plus, pour ne pas surcharger le serveur.
  for (let i = 0; i < suites.length; i += 10) {
    const reponses = await Promise.all(suites.slice(i, i + 10).map(({ saison, debut, fin }) =>
      supabase
        .from('episodes_vus')
        .delete()
        .eq('serie_id', serieId)
        .eq('saison', saison)
        .gte('episode', debut)
        .lte('episode', fin)));
    for (const { error } of reponses) {
      if (error) throw new Error(traduireErreur(error));
    }
  }
}

// Regroupe des épisodes en suites sans trou, saison par saison :
// 1x1, 1x2, 1x3, 1x5 → { saison 1, de 1 à 3 } et { saison 1, de 5 à 5 }.
function suitesConsecutives(episodes) {
  const tries = [...episodes].sort((a, b) => a.saison - b.saison || a.episode - b.episode);
  const suites = [];
  for (const { saison, episode } of tries) {
    const derniere = suites.at(-1);
    if (derniere && derniere.saison === saison && episode <= derniere.fin + 1) {
      derniere.fin = Math.max(derniere.fin, episode); // suite qui continue (ou doublon)
    } else {
      suites.push({ saison, debut: episode, fin: episode });
    }
  }
  return suites;
}


// --- Fiche d'une série ---

// L'API de Supabase renvoie au plus 1 000 lignes par requête (réglage par
// défaut « Max rows ») : au-delà, il faut demander la suite page par page.
const LIGNES_PAR_PAGE = 1000;

// Colonnes de la vue « ma_liste » utiles à la fiche.
const COLONNES_SUIVI =
  'serie_id, statut, statut_manuel, nb_diffuses, nb_vus, prochaine_saison, prochain_episode, ' +
  'categorie_manuelle, est_anime';

/**
 * Tout ce qu'il faut pour afficher la fiche d'une série :
 * { serie, saisons, suivi, vus }. « suivi » vaut null si la série n'est pas
 * dans la liste de l'utilisateur. Les quatre lectures partent en même temps.
 */
export async function chargerFiche(serieId) {
  const [serie, saisons, suivi, vus] = await Promise.all([
    supabase
      .from('series')
      .select('id, tmdb_id, titre, titre_original, affiche_chemin, statut_diffusion, ' +
              'derniere_saison_diffusee, dernier_episode_diffuse, fiche_maj_le, est_anime')
      .eq('id', serieId)
      .maybeSingle(),
    supabase.from('saisons').select('numero, nb_episodes').eq('serie_id', serieId).order('numero'),
    supabase.from('ma_liste').select(COLONNES_SUIVI).eq('serie_id', serieId).maybeSingle(),
    lireEpisodesVus(serieId),
  ]);
  for (const reponse of [serie, saisons, suivi]) {
    if (reponse.error) throw new Error(traduireErreur(reponse.error));
  }
  return { serie: serie.data, saisons: saisons.data, suivi: suivi.data, vus };
}

/** Relit la ligne de « ma_liste » d'une série (compteurs, prochain épisode, statut). */
export async function lireSuivi(serieId) {
  const { data, error } = await supabase
    .from('ma_liste')
    .select(COLONNES_SUIVI)
    .eq('serie_id', serieId)
    .maybeSingle();
  if (error) throw new Error(traduireErreur(error));
  return data;
}

// Lit TOUTES les lignes d'une requête, page par page (voir LIGNES_PAR_PAGE).
// « construire » fabrique la requête à chaque page ; elle doit avoir un ordre
// stable (order), sinon une ligne pourrait être lue deux fois ou oubliée.
async function lireParPages(construire) {
  const tous = [];
  for (let debut = 0; ; debut += LIGNES_PAR_PAGE) {
    const { data, error } = await construire().range(debut, debut + LIGNES_PAR_PAGE - 1);
    if (error) throw new Error(traduireErreur(error));
    tous.push(...data);
    if (data.length < LIGNES_PAR_PAGE) return tous;
  }
}

// Épisodes cochés d'une série (un long animé peut dépasser 1 000).
function lireEpisodesVus(serieId) {
  return lireParPages(() => supabase
    .from('episodes_vus')
    .select('saison, episode')
    .eq('serie_id', serieId)
    .order('saison')
    .order('episode'));
}

/** Choisit un statut à la main ; null = revenir au statut automatique. */
export async function changerStatut(serieId, statutManuel) {
  const { error } = await supabase
    .from('suivis')
    .update({ statut_manuel: statutManuel })
    .eq('serie_id', serieId);
  if (error) throw new Error(traduireErreur(error));
}

/**
 * Corrige à la main le classement d'une série : 'serie' ou 'anime' ;
 * null = revenir au classement automatique.
 */
export async function changerCategorie(serieId, categorie) {
  const { error } = await supabase
    .from('suivis')
    .update({ categorie_manuelle: categorie })
    .eq('serie_id', serieId);
  if (error) throw new Error(traduireErreur(error));
}

/**
 * Retire une série de la liste. Ses épisodes cochés sont effacés avec elle
 * (« on delete cascade » dans la base, décision 16).
 */
export async function retirerSerie(serieId) {
  const { error } = await supabase.from('suivis').delete().eq('serie_id', serieId);
  if (error) throw new Error(traduireErreur(error));
}


// --- Sauvegarde ---

/**
 * Toutes les données de l'utilisateur connecté, pour la sauvegarde : ses
 * séries suivies et, pour chacune, ses épisodes cochés. Chaque série est
 * repérée par ses identifiants TMDB, IMDb et TVDB (pas par notre identifiant
 * interne), pour pouvoir la retrouver ailleurs ou la réimporter plus tard.
 */
export async function lireDonneesPourExport() {
  const [suivis, vus] = await Promise.all([
    lireParPages(() => supabase
      .from('suivis')
      .select('serie_id, statut_manuel, categorie_manuelle, ajoute_le, ' +
              'series (tmdb_id, imdb_id, tvdb_id, titre, titre_original)')
      .order('ajoute_le')
      .order('serie_id')),
    lireParPages(() => supabase
      .from('episodes_vus')
      .select('serie_id, saison, episode, vu_le')
      .order('serie_id')
      .order('saison')
      .order('episode')),
  ]);

  // Regroupe les épisodes cochés par série.
  const vusParSerie = new Map();
  for (const v of vus) {
    if (!vusParSerie.has(v.serie_id)) vusParSerie.set(v.serie_id, []);
    vusParSerie.get(v.serie_id).push({ saison: v.saison, episode: v.episode, vu_le: v.vu_le });
  }

  return suivis.map((s) => ({
    tmdb_id: s.series?.tmdb_id ?? null,
    imdb_id: s.series?.imdb_id ?? null,
    tvdb_id: s.series?.tvdb_id ?? null,
    titre: s.series?.titre ?? null,
    titre_original: s.series?.titre_original ?? null,
    statut_manuel: s.statut_manuel,             // null = statut automatique
    categorie_manuelle: s.categorie_manuelle,   // null = classement automatique
    ajoute_le: s.ajoute_le,
    episodes_vus: vusParSerie.get(s.serie_id) ?? [],
  }));
}


// --- Import d'une sauvegarde (décision 40) ---

// Nombre de lignes envoyées par requête lors d'un import (choix de Claude, à revoir).
const LIGNES_PAR_ENVOI = 500;

/**
 * Identifiants internes des séries déjà présentes dans le catalogue, à partir
 * de leurs identifiants TMDB. Renvoie une Map tmdbId → identifiant interne.
 */
export async function idsCatalogue(tmdbIds) {
  const ids = new Map();
  // 200 identifiants par requête : ils passent dans l'adresse de la requête.
  for (let i = 0; i < tmdbIds.length; i += 200) {
    const { data, error } = await supabase
      .from('series')
      .select('id, tmdb_id')
      .in('tmdb_id', tmdbIds.slice(i, i + 200));
    if (error) throw new Error(traduireErreur(error));
    for (const s of data) ids.set(s.tmdb_id, s.id);
  }
  return ids;
}

/**
 * Ajoute des séries à la liste avec leur date d'ajout et leur catégorie
 * d'origine : lignes [{ serie_id, categorie_manuelle, ajoute_le }].
 * Une série déjà suivie est laissée telle quelle (« ignoreDuplicates »).
 */
export async function suivreSeriesImportees(lignes) {
  for (let i = 0; i < lignes.length; i += LIGNES_PAR_ENVOI) {
    const { error } = await supabase
      .from('suivis')
      .upsert(lignes.slice(i, i + LIGNES_PAR_ENVOI), { onConflict: 'utilisateur_id,serie_id', ignoreDuplicates: true });
    if (error) throw new Error(traduireErreur(error));
  }
}

/**
 * Coche des épisodes avec leur date d'origine : lignes
 * [{ serie_id, saison, episode, vu_le }]. Un épisode déjà coché est laissé
 * tel quel (sa date ne change pas).
 */
export async function cocherEpisodesImportes(lignes) {
  for (let i = 0; i < lignes.length; i += LIGNES_PAR_ENVOI) {
    const { error } = await supabase
      .from('episodes_vus')
      .upsert(lignes.slice(i, i + LIGNES_PAR_ENVOI), { onConflict: 'utilisateur_id,serie_id,saison,episode', ignoreDuplicates: true });
    if (error) throw new Error(traduireErreur(error));
  }
}

/**
 * Retire toutes les séries de la liste de l'utilisateur connecté, et avec
 * elles leurs épisodes cochés (« on delete cascade », comme pour un retrait).
 * Les règles RLS limitent la suppression à ses propres suivis. Le filtre
 * « serie_id non vide » est toujours vrai ; il est là par précaution, une
 * suppression sans aucun filtre pouvant être refusée (non vérifié).
 */
export async function viderListe() {
  const { error } = await supabase.from('suivis').delete().not('serie_id', 'is', null);
  if (error) throw new Error(traduireErreur(error));
}


// --- Catalogue (décision 42) ---

/**
 * Retire du catalogue les séries que plus personne ne suit et dont la fiche
 * TMDB a plus de 5 mois (les conditions de TMDB interdisent de garder ses
 * informations plus de 6 mois). Le travail est fait dans la base par la
 * fonction SQL « nettoyer_catalogue » : les utilisateurs ne peuvent pas
 * supprimer de séries eux-mêmes. Renvoie le nombre de séries supprimées.
 */
export async function nettoyerCatalogue() {
  const { data, error } = await supabase.rpc('nettoyer_catalogue');
  if (error) throw new Error(traduireErreur(error));
  return data;
}

/**
 * Points, niveau, rang et badges de l'utilisateur connecté (vue
 * « ma_progression », décision 43) : une seule ligne, entièrement calculée
 * par la base à partir des épisodes cochés. « badges » est la liste des
 * 18 badges, dans l'ordre d'affichage (voir le fichier SQL pour leur forme).
 */
export async function lireProgression() {
  const { data, error } = await supabase
    .from('ma_progression')
    .select(
      'points, niveau, points_niveau, points_niveau_suivant, ' +
      'rang_numero, rang, rang_suivant, niveau_rang_suivant, badges'
    )
    .single();
  if (error) throw new Error(traduireErreur(error));
  return data;
}


// Transforme une erreur renvoyée par Supabase en message compréhensible.
// Les codes ci-dessous sont ceux attendus pour la connexion (à confirmer en test) ;
// pour tout autre cas, on affiche le message d'origine.
function traduireErreur(error) {
  switch (error.code) {
    case 'invalid_credentials':
      return 'E-mail ou mot de passe incorrect.';
    case 'email_not_confirmed':
      return "Cette adresse e-mail n'a pas encore été confirmée.";
    case 'over_request_rate_limit':
      return 'Trop de tentatives. Patientez quelques minutes avant de réessayer.';
    default:
      return `Erreur : ${error.message}`;
  }
}
