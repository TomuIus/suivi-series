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
