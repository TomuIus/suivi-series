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
      'nb_diffuses, nb_vus, prochaine_saison, prochain_episode, derniere_activite'
    )
    .order('derniere_activite', { ascending: false });
  if (error) throw new Error(traduireErreur(error));
  return data;
}

/**
 * Ajoute une série du catalogue (identifiant interne) à la liste de
 * l'utilisateur connecté. Renvoie false si elle y était déjà.
 */
export async function suivreSerie(serieId) {
  // utilisateur_id n'est pas envoyé : la base le remplit avec auth.uid().
  const { error } = await supabase.from('suivis').insert({ serie_id: serieId });
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
