// Écran « fiche d'une série » : son état et ses actions.
//
// C'est un « composable » Vue : une fonction qui regroupe les variables
// réactives (ref) et les actions d'une partie de l'application. app.js
// l'appelle dans setup() et rend son contenu utilisable dans le HTML.
// Avantage : app.js reste lisible, et tout ce qui concerne la fiche est ici.

import { ref, computed } from 'vue';
import {
  chargerFiche,
  lireSuivi,
  marquerVus,
  annulerVu,
  changerStatut,
  retirerSerie,
} from './base.js';
import { importerSerie } from './tmdb.js';

// Au-delà de ce délai, la fiche est remise à jour depuis TMDB quand on
// l'ouvre (décision 12 : nouveaux épisodes, statut de diffusion…).
const DELAI_MISE_A_JOUR = 24 * 60 * 60 * 1000; // 24 h — à revoir après des tests

const SERIES_FINIES = ['Ended', 'Canceled'];

// Clé d'un épisode dans l'ensemble des épisodes vus : « 2-5 » = saison 2, épisode 5.
const cle = (saison, episode) => `${saison}-${episode}`;

/**
 * @param erreur        la variable d'erreur de l'application (affichée en haut)
 * @param apresRetrait  fonction appelée avec le titre quand la série a été retirée
 */
export function utiliserFiche({ erreur, apresRetrait }) {
  const fiche = ref(null);         // { serie, saisons, suivi, vus, saisonInitiale } ou null
  const ficheChargee = ref(false);
  const invite = ref(null);        // proposition « cocher les précédents » : { episodes } ou null
  const confirmationRetrait = ref(false);
  const statutEnCours = ref(false);

  let numeroFiche = 0;  // permet d'ignorer les réponses d'une fiche qu'on a quittée
  let numeroSuivi = 0;  // idem pour la relecture des compteurs

  // Statut que le site calculerait tout seul (même règle que la vue « ma_liste »),
  // affiché à côté de « Automatique » dans le choix du statut.
  const statutCalcule = computed(() => {
    const suivi = fiche.value?.suivi;
    if (!suivi) return null;
    if (suivi.nb_vus === 0) return 'a_voir';
    if (suivi.nb_vus < suivi.nb_diffuses) return 'en_cours';
    return SERIES_FINIES.includes(fiche.value.serie.statut_diffusion) ? 'termine' : 'a_jour';
  });

  // --- Ouverture et fermeture ---

  async function ouvrirFiche(serieId) {
    const numero = ++numeroFiche;
    fiche.value = null;
    ficheChargee.value = false;
    invite.value = null;
    confirmationRetrait.value = false;
    try {
      const donnees = await chargerFiche(serieId);
      if (numero !== numeroFiche) return;
      fiche.value = preparer(donnees);
      if (fiche.value) mettreAJourSiAncienne(numero);
    } catch (e) {
      if (numero === numeroFiche) erreur.value = e.message;
    } finally {
      if (numero === numeroFiche) ficheChargee.value = true;
    }
  }

  function fermerFiche() {
    numeroFiche++;
    fiche.value = null;
    invite.value = null;
    confirmationRetrait.value = false;
  }

  // Met les données reçues en forme pour l'affichage. Renvoie null si la
  // série n'existe pas ou n'est pas dans la liste de l'utilisateur.
  function preparer({ serie, saisons, suivi, vus }) {
    if (!serie || !suivi) return null;
    const normales = saisons.filter((s) => s.numero > 0);
    const speciaux = saisons.filter((s) => s.numero === 0);
    return {
      serie,
      suivi,
      saisons: [...normales, ...speciaux],  // épisodes spéciaux en dernier
      vus: new Set(vus.map((v) => cle(v.saison, v.episode))),
      // Saison ouverte au départ : celle du prochain épisode, sinon la dernière.
      saisonInitiale: suivi.prochaine_saison ?? normales.at(-1)?.numero ?? 0,
    };
  }

  // Si la fiche date de plus de 24 h, on la remet à jour depuis TMDB, en
  // arrière-plan : la fiche s'affiche tout de suite, puis se corrige.
  async function mettreAJourSiAncienne(numero) {
    const { serie } = fiche.value;
    if (Date.now() - Date.parse(serie.fiche_maj_le) < DELAI_MISE_A_JOUR) return;
    try {
      await importerSerie(serie.tmdb_id);
      const donnees = await chargerFiche(serie.id);
      if (numero !== numeroFiche || !fiche.value) return;
      const nouvelle = preparer(donnees);
      if (!nouvelle) return;
      // On garde la saison ouverte et les cases cochées entre-temps.
      nouvelle.saisonInitiale = fiche.value.saisonInitiale;
      nouvelle.vus = fiche.value.vus;
      fiche.value = nouvelle;
    } catch (e) {
      // Pas grave : on garde la fiche actuelle. Le détail va dans la console.
      console.warn('Mise à jour de la fiche depuis TMDB impossible :', e.message);
    }
  }

  // Relit compteurs, prochain épisode et statut après une modification.
  async function actualiserSuivi(serieId) {
    const numero = ++numeroSuivi;
    try {
      const suivi = await lireSuivi(serieId);
      if (numero === numeroSuivi && suivi && fiche.value?.serie.id === serieId) {
        fiche.value.suivi = suivi;
      }
    } catch (e) {
      console.warn('Relecture des compteurs impossible :', e.message);
    }
  }

  // --- Épisodes ---

  // Un épisode est-il déjà sorti ? (même règle que la vue « ma_liste » ;
  // les épisodes spéciaux restent toujours cochables, décision 14)
  function estDiffuse(saison, episode) {
    if (saison === 0) return true;
    const serie = fiche.value.serie;
    if (SERIES_FINIES.includes(serie.statut_diffusion)) return true;
    const derniereSaison = serie.derniere_saison_diffusee;
    if (derniereSaison == null) return false;
    return saison < derniereSaison
      || (saison === derniereSaison && episode <= serie.dernier_episode_diffuse);
  }

  // « vu », « non-vu » ou « a-venir » (pas encore sorti) : sert à l'affichage.
  function etatEpisode(saison, episode) {
    if (fiche.value.vus.has(cle(saison, episode))) return 'vu';
    return estDiffuse(saison, episode) ? 'non-vu' : 'a-venir';
  }

  function nbVusSaison(saison) {
    let total = 0;
    for (let e = 1; e <= saison.nb_episodes; e++) {
      if (fiche.value.vus.has(cle(saison.numero, e))) total++;
    }
    return total;
  }

  // Appui sur un épisode : on le coche ou on le décoche. L'affichage change
  // tout de suite ; si le serveur refuse, on revient en arrière.
  async function basculer(saison, episode) {
    const f = fiche.value;
    if (!f) return;
    const k = cle(saison, episode);
    const serieId = f.serie.id;
    erreur.value = '';

    if (f.vus.has(k)) {
      f.vus.delete(k);
      invite.value = null;
      try {
        await annulerVu(serieId, saison, episode);
      } catch (e) {
        f.vus.add(k);
        erreur.value = e.message;
      }
    } else {
      f.vus.add(k);
      try {
        await marquerVus(serieId, [{ saison, episode }]);
        proposerPrecedents(saison, episode);
      } catch (e) {
        f.vus.delete(k);
        erreur.value = e.message;
      }
    }
    actualiserSuivi(serieId);
  }

  // Après avoir coché un épisode : s'il reste des épisodes sortis AVANT lui
  // qui ne sont pas cochés, on propose de les cocher aussi (« tout cocher
  // jusqu'ici », utile pour une série commencée ailleurs).
  function proposerPrecedents(saison, episode) {
    invite.value = null;
    if (saison === 0) return; // les spéciaux ne comptent pas
    const manquants = [];
    for (const s of fiche.value.saisons) {
      if (s.numero === 0 || s.numero > saison) continue;
      for (let e = 1; e <= s.nb_episodes; e++) {
        if (s.numero === saison && e >= episode) break;
        if (estDiffuse(s.numero, e) && !fiche.value.vus.has(cle(s.numero, e))) {
          manquants.push({ saison: s.numero, episode: e });
        }
      }
    }
    if (manquants.length > 0) invite.value = { episodes: manquants };
  }

  async function cocherPrecedents() {
    const proposition = invite.value;
    const f = fiche.value;
    invite.value = null;
    if (!proposition || !f) return;
    const cles = proposition.episodes.map(({ saison, episode }) => cle(saison, episode));
    cles.forEach((k) => f.vus.add(k));
    try {
      await marquerVus(f.serie.id, proposition.episodes);
    } catch (e) {
      cles.forEach((k) => f.vus.delete(k));
      erreur.value = e.message;
    }
    actualiserSuivi(f.serie.id);
  }

  function refuserInvite() {
    invite.value = null;
  }

  // --- Statut choisi à la main ---

  async function modifierStatut(valeur) {
    const f = fiche.value;
    if (!f) return;
    statutEnCours.value = true;
    erreur.value = '';
    try {
      await changerStatut(f.serie.id, valeur || null); // '' → automatique
      await actualiserSuivi(f.serie.id);
    } catch (e) {
      erreur.value = e.message;
    } finally {
      statutEnCours.value = false;
    }
  }

  // --- Retrait de la liste (avec confirmation, décision 16) ---

  function demanderRetrait() {
    confirmationRetrait.value = true;
  }

  function annulerRetrait() {
    confirmationRetrait.value = false;
  }

  async function confirmerRetrait() {
    const f = fiche.value;
    if (!f) return;
    erreur.value = '';
    try {
      await retirerSerie(f.serie.id);
      confirmationRetrait.value = false;
      apresRetrait(f.serie.titre);
    } catch (e) {
      erreur.value = e.message;
    }
  }

  return {
    fiche, ficheChargee, invite, confirmationRetrait, statutEnCours, statutCalcule,
    ouvrirFiche, fermerFiche, etatEpisode, nbVusSaison, basculer,
    cocherPrecedents, refuserInvite, modifierStatut,
    demanderRetrait, annulerRetrait, confirmerRetrait,
  };
}
