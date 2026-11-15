// Écran « fiche d'une série » : son état et ses actions.
//
// C'est un « composable » Vue : une fonction qui regroupe les variables
// réactives (ref) et les actions d'une partie de l'application. app.js
// l'appelle dans setup() et rend son contenu utilisable dans le HTML.
// Avantage : app.js reste lisible, et tout ce qui concerne la fiche est ici.
//
// La fiche existe en deux modes :
//   - série suivie : on lit la base (épisodes cochés, statut…), on peut cocher ;
//   - aperçu : série pas (encore) dans la liste ; on lit TMDB ou le catalogue,
//     rien n'est cochable, un bouton permet de l'ajouter à sa liste.

import { ref, computed } from 'vue';
import {
  chargerFiche,
  lireSuivi,
  suivreSerie,
  marquerVus,
  annulerVu,
  annulerVus,
  changerStatut,
  changerCategorie,
  retirerSerie,
} from './base.js';
import { importerSerie, apercuSerie, episodesSaison } from './tmdb.js';

// Au-delà de ce délai, la fiche d'une série du catalogue est remise à jour
// depuis TMDB quand on l'ouvre (décision 12 : nouveaux épisodes, statut…).
const DELAI_MISE_A_JOUR = 24 * 60 * 60 * 1000; // 24 h — à revoir après des tests

const SERIES_FINIES = ['Ended', 'Canceled'];

// Statuts qui veulent dire « j'ai tout vu » : les choisir à la main coche tous
// les épisodes déjà sortis, sauf les spéciaux (décision 36).
const STATUTS_TOUT_VU = ['a_jour', 'termine'];

// Clé d'un épisode dans l'ensemble des épisodes vus : « 2-5 » = saison 2, épisode 5.
const cle = (saison, episode) => `${saison}-${episode}`;
// Clé d'une saison dans la mémoire des titres : « 1399-2 » = série TMDB 1399, saison 2.
const cleSaison = (tmdbId, saison) => `${tmdbId}-${saison}`;

// Dates en français : « 29 sept. 2023 ».
const formatDate = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });

/**
 * @param erreur        la variable d'erreur de l'application (affichée en haut)
 * @param apresRetrait  appelée avec le titre quand la série a été retirée de la liste
 * @param apresAjout    appelée avec (identifiant interne, titre, plusTard) quand la série a été ajoutée
 * @param afficherAnnulable  affiche un message (texte) avec un bouton « Annuler » qui appelle annuler()
 */
export function utiliserFiche({ erreur, apresRetrait, apresAjout, afficherAnnulable }) {
  const fiche = ref(null);         // { serie, saisons, suivi, vus, saisonInitiale, resume } ou null
  const ficheChargee = ref(false);
  const invite = ref(null);        // proposition « cocher les précédents » : { episodes } ou null
  const confirmationRetrait = ref(false);
  const statutEnCours = ref(false);
  const categorieEnCours = ref(false);
  const ajoutEnCours = ref(null);  // ajout en cours : 'liste' ou 'plus_tard' (bouton appuyé), sinon null
  // Titres des épisodes déjà demandés à TMDB, gardés le temps de la visite :
  // clé de saison → { etat: 'chargement' | 'ok' | 'erreur', episodes: Map(numéro → { titre, date }) }
  const titres = ref(new Map());

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

  // Classement automatique (TMDB) : affiché à côté de « Automatique » dans le
  // choix de la catégorie.
  const categorieCalculee = computed(() => (fiche.value?.serie.est_anime ? 'anime' : 'serie'));

  // La série est-elle un animé ? (correction de l'utilisateur, sinon classement automatique)
  const estAnimeFiche = computed(() => {
    const f = fiche.value;
    if (!f) return false;
    return f.suivi ? f.suivi.est_anime : f.serie.est_anime === true;
  });

  // Titre du prochain épisode, dès que sa saison a été chargée.
  const titreProchain = computed(() => {
    const suivi = fiche.value?.suivi;
    if (!suivi || suivi.prochaine_saison == null) return null;
    return infosEpisode(suivi.prochaine_saison, suivi.prochain_episode)?.titre ?? null;
  });

  // --- Ouverture et fermeture ---

  // cible = { serieId } (série du catalogue, suivie ou non) ou { tmdbId } (aperçu)
  async function ouvrirFiche(cible) {
    const numero = ++numeroFiche;
    fiche.value = null;
    ficheChargee.value = false;
    invite.value = null;
    confirmationRetrait.value = false;
    try {
      if (cible.serieId != null) {
        const donnees = await chargerFiche(cible.serieId);
        if (numero !== numeroFiche) return;
        fiche.value = preparer(donnees);
        if (fiche.value) completerDepuisTmdb(numero);
      } else {
        const donnees = await apercuSerie(cible.tmdbId);
        if (numero !== numeroFiche) return;
        fiche.value = preparer({ ...donnees, suivi: null, vus: [] });
      }
      if (fiche.value) chargerTitres(fiche.value.saisonInitiale);
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

  // Met les données reçues en forme pour l'affichage (null si la série n'existe pas).
  // « suivi » vaut null quand la série n'est pas dans la liste : mode aperçu.
  function preparer({ serie, saisons, suivi, vus }) {
    if (!serie) return null;
    const normales = saisons.filter((s) => s.numero > 0);
    const speciaux = saisons.filter((s) => s.numero === 0);
    return {
      serie,
      suivi,
      saisons: [...normales, ...speciaux],  // épisodes spéciaux en dernier
      vus: new Set(vus.map((v) => cle(v.saison, v.episode))),
      // Saison ouverte au départ : pour une série suivie, celle du prochain
      // épisode (sinon la dernière) ; pour un aperçu, la première.
      saisonInitiale: suivi
        ? (suivi.prochaine_saison ?? normales.at(-1)?.numero ?? 0)
        : (normales[0]?.numero ?? 0),
      resume: serie.resume ?? null,
    };
  }

  // Série du catalogue : on complète avec TMDB, en arrière-plan (la fiche
  // s'affiche tout de suite). Si la fiche date de plus de 24 h, ou n'a pas
  // encore de classement série / animé, on la remet à jour dans le catalogue
  // (ce qui donne aussi le résumé) ; sinon on demande seulement le résumé,
  // sans rien enregistrer.
  async function completerDepuisTmdb(numero) {
    const { serie } = fiche.value;
    const ancienne = Date.now() - Date.parse(serie.fiche_maj_le) >= DELAI_MISE_A_JOUR
      || serie.est_anime == null;
    try {
      if (!ancienne) {
        const apercu = await apercuSerie(serie.tmdb_id);
        if (numero === numeroFiche && fiche.value) fiche.value.resume = apercu.serie.resume;
        return;
      }
      const { resume } = await importerSerie(serie.tmdb_id);
      const donnees = await chargerFiche(serie.id);
      if (numero !== numeroFiche || !fiche.value) return;
      const nouvelle = preparer(donnees);
      if (!nouvelle) return;
      // On garde la saison ouverte et les cases cochées entre-temps.
      nouvelle.saisonInitiale = fiche.value.saisonInitiale;
      nouvelle.vus = fiche.value.vus;
      nouvelle.resume = resume;
      fiche.value = nouvelle;
    } catch (e) {
      // Pas grave : on garde la fiche actuelle. Le détail va dans la console.
      console.warn('Complément de la fiche depuis TMDB impossible :', e.message);
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

  // --- Titres et dates des épisodes (demandés à TMDB saison par saison) ---

  // Appelée à l'ouverture de la fiche et quand on déplie une saison.
  async function chargerTitres(saison) {
    const tmdbId = fiche.value?.serie.tmdb_id;
    if (tmdbId == null) return;
    const k = cleSaison(tmdbId, saison);
    const connu = titres.value.get(k);
    if (connu && connu.etat !== 'erreur') return; // déjà chargé ou en cours
    titres.value.set(k, { etat: 'chargement', episodes: new Map() });
    try {
      const episodes = await episodesSaison(tmdbId, saison);
      titres.value.set(k, { etat: 'ok', episodes: new Map(episodes.map((e) => [e.numero, e])) });
    } catch (e) {
      titres.value.set(k, { etat: 'erreur', episodes: new Map() });
      console.warn(`Titres de la saison ${saison} indisponibles :`, e.message);
    }
  }

  function infosEpisode(saison, episode) {
    const tmdbId = fiche.value?.serie.tmdb_id;
    return titres.value.get(cleSaison(tmdbId, saison))?.episodes.get(episode) ?? null;
  }

  // Titre affiché : celui de TMDB, sinon « … » pendant le chargement, sinon « Épisode 5 ».
  function titreEpisode(saison, episode) {
    const infos = infosEpisode(saison, episode);
    if (infos?.titre) return infos.titre;
    const etat = titres.value.get(cleSaison(fiche.value.serie.tmdb_id, saison))?.etat;
    return etat === 'chargement' ? '…' : `Épisode ${episode}`;
  }

  // Date de sortie affichée (« 29 sept. 2023 »), ou '' si TMDB ne la donne pas.
  function dateEpisode(saison, episode) {
    const date = infosEpisode(saison, episode)?.date;
    if (!date) return '';
    const texte = formatDate.format(new Date(`${date}T00:00:00`));
    return etatEpisode(saison, episode) === 'a-venir' ? `à venir · ${texte}` : texte;
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
    if (!f || !f.suivi) return; // en aperçu, rien n'est cochable
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

  // --- Ajout à la liste depuis l'aperçu ---

  // plusTard = true : bouton « Plus tard » (la série va directement dans
  // cette section, décision 26).
  async function ajouterALaListe(plusTard = false) {
    const f = fiche.value;
    if (!f || f.suivi || ajoutEnCours.value) return;
    ajoutEnCours.value = plusTard ? 'plus_tard' : 'liste';
    erreur.value = '';
    try {
      // Série déjà dans le catalogue : on a son identifiant. Sinon, on l'y copie d'abord.
      const serie = f.serie.id != null
        ? { id: f.serie.id, titre: f.serie.titre }
        : await importerSerie(f.serie.tmdb_id);
      const nouvelle = await suivreSerie(serie.id, { plusTard });
      apresAjout(serie.id, serie.titre, plusTard && nouvelle);
    } catch (e) {
      erreur.value = e.message;
    } finally {
      ajoutEnCours.value = null;
    }
  }

  // --- Statut choisi à la main ---

  // Passer une série à « Terminé » ou « À jour » coche aussi tous les
  // épisodes déjà sortis qui ne l'étaient pas, sauf les spéciaux (décision 36).
  // Le statut est changé d'abord : ainsi, une série « Plus tard » passe bien
  // à « Terminé » (le déclencheur de la base qui la sort de « Plus tard »
  // quand on coche ne s'applique plus). Un message propose ensuite
  // « Annuler » pendant 8 s : épisodes décochés et ancien statut remis.
  async function modifierStatut(valeur) {
    const f = fiche.value;
    if (!f || !f.suivi) return;
    const nouveau = valeur || null;                // '' → automatique
    const ancien = f.suivi.statut_manuel ?? null;
    // Liste établie avant tout envoi, sur la fiche affichée à ce moment-là.
    const aCocher = STATUTS_TOUT_VU.includes(nouveau) ? episodesNonVus() : [];
    statutEnCours.value = true;
    erreur.value = '';
    try {
      await changerStatut(f.serie.id, nouveau);
      if (aCocher.length > 0) await cocherTout(f, aCocher, ancien);
      await actualiserSuivi(f.serie.id);
    } catch (e) {
      erreur.value = e.message;
    } finally {
      statutEnCours.value = false;
    }
  }

  // Épisodes déjà sortis, hors spéciaux, pas encore cochés.
  function episodesNonVus() {
    const manquants = [];
    for (const s of fiche.value.saisons) {
      if (s.numero === 0) continue;
      for (let e = 1; e <= s.nb_episodes; e++) {
        if (estDiffuse(s.numero, e) && !fiche.value.vus.has(cle(s.numero, e))) {
          manquants.push({ saison: s.numero, episode: e });
        }
      }
    }
    return manquants;
  }

  async function cocherTout(f, episodes, ancienStatut) {
    invite.value = null; // la proposition « cocher les précédents » n'a plus lieu d'être
    const cles = episodes.map(({ saison, episode }) => cle(saison, episode));
    cles.forEach((k) => f.vus.add(k));
    try {
      await marquerVus(f.serie.id, episodes);
    } catch (e) {
      cles.forEach((k) => f.vus.delete(k));
      throw e;
    }
    const n = episodes.length;
    afficherAnnulable(
      `« ${f.serie.titre} » : ${n === 1 ? '1 épisode coché' : `${n} épisodes cochés`}.`,
      () => annulerToutCocher(f, episodes, ancienStatut),
    );
  }

  // Bouton « Annuler » du message : on décoche les épisodes qu'on vient de
  // cocher (et seulement eux) et on remet le statut d'avant. Renvoie le texte
  // de confirmation à afficher (rien en cas d'erreur).
  async function annulerToutCocher(f, episodes, ancienStatut) {
    erreur.value = '';
    const serieId = f.serie.id;
    try {
      await annulerVus(serieId, episodes);
      await changerStatut(serieId, ancienStatut);
    } catch (e) {
      erreur.value = e.message;
      return null;
    }
    // On met à jour la fiche d'origine et, si on a rouvert la même série
    // entre-temps, la fiche affichée.
    const cles = episodes.map(({ saison, episode }) => cle(saison, episode));
    for (const cible of new Set([f, fiche.value])) {
      if (cible?.serie.id === serieId) cles.forEach((k) => cible.vus.delete(k));
    }
    await actualiserSuivi(serieId);
    return `« ${f.serie.titre} » : épisodes décochés et statut remis comme avant.`;
  }

  // --- Catégorie corrigée à la main (série ou animé) ---

  async function modifierCategorie(valeur) {
    const f = fiche.value;
    if (!f || !f.suivi) return;
    categorieEnCours.value = true;
    erreur.value = '';
    try {
      await changerCategorie(f.serie.id, valeur || null); // '' → automatique
      await actualiserSuivi(f.serie.id);
    } catch (e) {
      erreur.value = e.message;
    } finally {
      categorieEnCours.value = false;
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
    fiche, ficheChargee, invite, confirmationRetrait, statutEnCours, categorieEnCours, ajoutEnCours,
    statutCalcule, categorieCalculee, estAnimeFiche, titreProchain,
    ouvrirFiche, fermerFiche, chargerTitres, titreEpisode, dateEpisode,
    etatEpisode, nbVusSaison, basculer,
    cocherPrecedents, refuserInvite, ajouterALaListe, modifierStatut, modifierCategorie,
    demanderRetrait, annulerRetrait, confirmerRetrait,
  };
}
