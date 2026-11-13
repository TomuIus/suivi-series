// Point d'entrée de l'application : crée l'application Vue et la relie à
// l'élément #app de index.html.
//
// Rappels Vue :
// - « ref(...) » crée une variable « réactive » : quand sa valeur change
//   (maVariable.value = ...), Vue met à jour tout seul la page.
// - « computed(...) » crée une valeur calculée à partir d'autres, recalculée
//   automatiquement quand elles changent.
// - « watch(x, ...) » exécute du code à chaque changement de x.

import { createApp, ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
import {
  utilisateurConnecte,
  surChangementDeConnexion,
  seConnecter,
  seDeconnecter,
  listerSuivis,
  suivreSerie,
  marquerVu,
  annulerVu,
} from './base.js';
import { rechercherSeries, importerSerie, urlAffiche } from './tmdb.js';
import { utiliserFiche } from './fiche.js';

// Réglages de la recherche
const LONGUEUR_MIN = 2;      // caractères avant de lancer une recherche
const DELAI_FRAPPE = 500;    // ms d'attente après la dernière touche — à revoir après des tests
const DUREE_MESSAGE = 5000;  // ms d'affichage d'un message de confirmation
const DUREE_ANNULATION = 8000; // ms pendant lesquels « Annuler » reste proposé

// Sections de « Ma liste », dans l'ordre d'affichage. Les sections « repliées »
// sont fermées au départ (on les ouvre d'un clic sur leur titre).
const SECTIONS = [
  { statut: 'en_cours',  titre: 'En cours',  repliee: false },
  { statut: 'a_voir',    titre: 'À voir',    repliee: false },
  { statut: 'a_jour',    titre: 'À jour',    repliee: false },
  { statut: 'termine',   titre: 'Terminé',   repliee: true },
  { statut: 'abandonne', titre: 'Abandonné', repliee: true },
];

// Libellés affichés pour les statuts (codes de la base) et pour les statuts
// de diffusion de TMDB.
const LIBELLES_STATUT = {
  a_voir: 'À voir',
  en_cours: 'En cours',
  a_jour: 'À jour',
  termine: 'Terminé',
  abandonne: 'Abandonné',
};
const LIBELLES_DIFFUSION = {
  'Returning Series': 'En diffusion',
  'In Production': 'En production',
  'Planned': 'Prévue',
  'Pilot': 'Pilote',
  'Ended': 'Terminée',
  'Canceled': 'Annulée',
};

// Quelle page afficher, d'après l'adresse : « #/serie/42 » = fiche de la
// série 42 ; toute autre adresse = « Ma liste ». Passer par l'adresse permet
// au bouton retour du navigateur (ou au geste retour du téléphone) de marcher.
function lireRoute() {
  const trouve = /^#\/serie\/(\d+)$/.exec(location.hash);
  return trouve ? { page: 'fiche', serieId: Number(trouve[1]) } : { page: 'liste' };
}

// Noms de pays en français à partir des codes de TMDB (« JP » → « Japon »).
let nomsDePays = null;
try {
  nomsDePays = new Intl.DisplayNames(['fr'], { type: 'region' });
} catch {
  // Navigateur trop ancien : on affichera le code brut.
}
function nomPays(code) {
  try {
    return nomsDePays?.of(code) ?? code;
  } catch {
    return code;
  }
}

createApp({
  setup() {
    // --- Connexion ---
    const utilisateur = ref(null);     // utilisateur connecté, ou null
    const chargement = ref(true);      // vrai tant qu'on vérifie la session au démarrage
    const email = ref('');             // champs du formulaire
    const motDePasse = ref('');
    const erreur = ref('');            // message d'erreur à afficher ('' = aucun)
    const envoiEnCours = ref(false);   // vrai pendant la tentative de connexion

    // --- Ma liste ---
    const liste = ref([]);             // séries suivies (lignes de la vue « ma_liste »)
    const listeChargee = ref(false);
    const vusEnCours = ref(new Set()); // serie_id des séries dont on coche un épisode

    // La liste découpée en sections ; les sections vides ne sont pas affichées.
    // L'ordre dans chaque section vient de la base : activité la plus récente d'abord.
    const sections = computed(() =>
      SECTIONS
        .map((section) => ({ ...section, series: liste.value.filter((s) => s.statut === section.statut) }))
        .filter((section) => section.series.length > 0)
    );

    // --- Recherche ---
    const recherche = ref('');            // texte tapé dans la barre
    const resultats = ref([]);            // résultats TMDB affichés
    const rechercheFaite = ref('');       // texte de la dernière recherche aboutie
    const rechercheEnCours = ref(false);
    const ajoutsEnCours = ref(new Set()); // tmdbId des séries en cours d'ajout
    const message = ref(null);            // confirmation : { texte, annuler } ou null

    // On affiche les résultats (à la place de la liste) dès qu'il y a du texte.
    const modeRecherche = computed(() => recherche.value.trim().length > 0);
    const rechercheTropCourte = computed(() => recherche.value.trim().length < LONGUEUR_MIN);
    // Identifiants TMDB des séries déjà suivies, pour afficher « Dans ma liste ».
    const idsDansListe = computed(() => new Set(liste.value.map((s) => s.tmdb_id)));

    // --- Navigation entre « Ma liste » et les fiches ---
    const route = ref(lireRoute());
    let depuisLaListe = false; // la fiche a-t-elle été ouverte depuis la liste ?
    let positionListe = 0;     // défilement de la liste, pour y revenir au même endroit

    // --- Fiche d'une série (voir fiche.js) ---
    const ecranFiche = utiliserFiche({
      erreur,
      apresRetrait: (titre) => {
        afficherMessage(`« ${titre} » ne fait plus partie de votre liste.`);
        retourListe();
      },
    });

    // Série interne correspondant à un identifiant TMDB (pour le lien
    // « voir la fiche » dans les résultats de recherche).
    const serieIdParTmdb = computed(() => new Map(liste.value.map((s) => [s.tmdb_id, s.serie_id])));

    let arreterEcoute = null;
    let minuterieFrappe = null;
    let minuterieMessage = null;
    let numeroRecherche = 0;  // permet d'ignorer la réponse d'une recherche dépassée
    let numeroChargement = 0; // idem pour le chargement de la liste

    // --- Au démarrage : y a-t-il déjà une session ouverte ? ---
    onMounted(async () => {
      window.addEventListener('hashchange', surChangementAdresse);
      arreterEcoute = surChangementDeConnexion((u) => {
        utilisateur.value = u;
      });
      try {
        utilisateur.value = await utilisateurConnecte();
      } catch {
        erreur.value = 'Impossible de joindre le serveur. Vérifiez votre connexion internet.';
      } finally {
        chargement.value = false;
      }
    });

    onUnmounted(() => {
      window.removeEventListener('hashchange', surChangementAdresse);
      arreterEcoute?.();
      clearTimeout(minuterieFrappe);
      clearTimeout(minuterieMessage);
    });

    // À chaque connexion (nouvel utilisateur), on charge sa liste ;
    // à la déconnexion, on efface de la page tout ce qui le concerne.
    watch(utilisateur, (nouveau, ancien) => {
      if (nouveau && nouveau.id !== ancien?.id) {
        chargerListe();
        if (route.value.page === 'fiche') ecranFiche.ouvrirFiche(route.value.serieId);
      }
      if (!nouveau) {
        ecranFiche.fermerFiche();
        liste.value = [];
        listeChargee.value = false;
        recherche.value = '';
        message.value = null;
      }
    });

    // Recherche « au fil de la frappe » : on attend une courte pause dans la
    // saisie avant d'interroger le serveur, pour ne pas lancer une recherche
    // à chaque lettre. La touche Entrée lance la recherche tout de suite.
    watch(recherche, () => {
      clearTimeout(minuterieFrappe);
      if (rechercheTropCourte.value) {
        numeroRecherche++; // une éventuelle réponse en attente sera ignorée
        resultats.value = [];
        rechercheFaite.value = '';
        rechercheEnCours.value = false;
        return;
      }
      minuterieFrappe = setTimeout(lancerRecherche, DELAI_FRAPPE);
    });

    // L'adresse a changé (lien vers une fiche, bouton retour…).
    function surChangementAdresse() {
      const avant = route.value;
      const apres = lireRoute();
      if (avant.page === 'liste' && apres.page === 'fiche') {
        depuisLaListe = true;
        positionListe = window.scrollY; // on note où on en était dans la liste
      }
      route.value = apres;
    }

    // À chaque changement de page : on charge ce qu'il faut.
    watch(route, async (r) => {
      if (!utilisateur.value) return;
      if (r.page === 'fiche') {
        window.scrollTo(0, 0);
        ecranFiche.ouvrirFiche(r.serieId);
      } else {
        ecranFiche.fermerFiche();
        await nextTick(); // attendre que la liste soit de nouveau affichée
        window.scrollTo(0, positionListe);
        chargerListe(); // la fiche a pu changer des épisodes ou le statut
      }
    });

    // Bouton « ← Ma liste » : comme le bouton retour si l'on vient de la liste
    // (on retrouve la liste à la même position), sinon on va à la liste.
    function retourListe() {
      if (depuisLaListe) {
        depuisLaListe = false;
        history.back();
      } else {
        location.hash = '#/';
      }
    }

    // --- Actions : connexion ---
    async function connexion() {
      erreur.value = '';
      envoiEnCours.value = true;
      try {
        utilisateur.value = await seConnecter(email.value, motDePasse.value);
        motDePasse.value = '';   // on n'en garde pas de copie inutile
      } catch (e) {
        erreur.value = e.message;
      } finally {
        envoiEnCours.value = false;
      }
    }

    async function deconnexion() {
      erreur.value = '';
      try {
        await seDeconnecter();
        utilisateur.value = null;
      } catch (e) {
        erreur.value = e.message;
      }
    }

    // --- Actions : liste ---
    async function chargerListe() {
      const numero = ++numeroChargement;
      try {
        const lignes = await listerSuivis();
        if (numero === numeroChargement) liste.value = lignes; // sinon, réponse dépassée
      } catch (e) {
        erreur.value = e.message;
      } finally {
        listeChargee.value = true;
      }
    }

    // Bouton « Vu » : coche le prochain épisode de la série, puis recharge la
    // liste (la base recalcule le prochain épisode et le statut).
    async function cocher(s) {
      if (vusEnCours.value.has(s.serie_id)) return; // évite un double appui
      const { serie_id: serieId, titre, prochaine_saison: saison, prochain_episode: episode } = s;
      vusEnCours.value.add(serieId);
      erreur.value = '';
      try {
        await marquerVu(serieId, saison, episode);
        await chargerListe();
        afficherMessage(
          `« ${titre} » : ${libelleEpisode(saison, episode)} vu.`,
          () => annuler(serieId, titre, saison, episode),
        );
      } catch (e) {
        erreur.value = e.message;
      } finally {
        vusEnCours.value.delete(serieId);
      }
    }

    // Bouton « Annuler » du message : décoche l'épisode qu'on vient de cocher.
    async function annuler(serieId, titre, saison, episode) {
      message.value = null;
      erreur.value = '';
      try {
        await annulerVu(serieId, saison, episode);
        await chargerListe();
        afficherMessage(`« ${titre} » : ${libelleEpisode(saison, episode)} n'est plus coché.`);
      } catch (e) {
        erreur.value = e.message;
      }
    }

    // --- Actions : recherche ---
    async function lancerRecherche() {
      clearTimeout(minuterieFrappe);
      const texte = recherche.value.trim();
      if (texte.length < LONGUEUR_MIN || texte === rechercheFaite.value) return;

      const numero = ++numeroRecherche;
      rechercheEnCours.value = true;
      erreur.value = '';
      try {
        const trouves = await rechercherSeries(texte);
        if (numero !== numeroRecherche) return; // une recherche plus récente a pris le relais
        resultats.value = trouves;
        rechercheFaite.value = texte;
      } catch (e) {
        if (numero === numeroRecherche) erreur.value = e.message;
      } finally {
        if (numero === numeroRecherche) rechercheEnCours.value = false;
      }
    }

    function effacerRecherche() {
      recherche.value = ''; // le « watch » ci-dessus remet la recherche à zéro
    }

    async function ajouter(resultat) {
      const id = resultat.tmdbId;
      if (ajoutsEnCours.value.has(id)) return; // évite un double clic
      ajoutsEnCours.value.add(id);
      erreur.value = '';
      try {
        const serie = await importerSerie(id);          // 1. fiche copiée dans le catalogue
        const nouvelle = await suivreSerie(serie.id);   // 2. série ajoutée à MA liste
        await chargerListe();
        afficherMessage(nouvelle
          ? `« ${serie.titre} » est maintenant dans votre liste.`
          : `« ${serie.titre} » était déjà dans votre liste.`);
      } catch (e) {
        erreur.value = e.message;
      } finally {
        ajoutsEnCours.value.delete(id);
      }
    }

    // Affiche une confirmation ; si « annuler » est fourni, un bouton
    // « Annuler » l'accompagne, un peu plus longtemps.
    function afficherMessage(texte, annuler = null) {
      message.value = { texte, annuler };
      clearTimeout(minuterieMessage);
      minuterieMessage = setTimeout(() => { message.value = null; },
        annuler ? DUREE_ANNULATION : DUREE_MESSAGE);
    }

    // --- Aides pour l'affichage ---
    // Ligne d'informations sous le titre : « 2011 · États-Unis · Titre original »
    function details(r) {
      const morceaux = [];
      if (r.annee) morceaux.push(r.annee);
      if (r.pays?.length) morceaux.push(r.pays.map(nomPays).join(', '));
      if (r.titreOriginal && r.titreOriginal !== r.titre) morceaux.push(r.titreOriginal);
      return morceaux.join(' · ');
    }

    // « S2 · É5 »
    function libelleEpisode(saison, episode) {
      return `S${saison} · É${episode}`;
    }

    // Le bouton « Vu » n'apparaît que s'il y a un épisode diffusé à cocher,
    // et seulement dans les sections « En cours » et « À voir ».
    function peutCocher(s) {
      return s.prochaine_saison != null && (s.statut === 'en_cours' || s.statut === 'a_voir');
    }

    // Ligne d'informations sous le titre d'une série de la liste.
    function progression(s) {
      const compte = `${s.nb_vus} / ${s.nb_diffuses} épisodes vus`;
      if (s.statut === 'termine') return `${s.nb_vus} épisodes vus`;
      if (s.statut === 'a_jour') return `${s.nb_vus} épisodes vus · en attente de la suite`;
      if (s.nb_diffuses === 0) return "Aucun épisode diffusé pour l'instant";
      if (s.prochaine_saison != null && s.statut !== 'abandonne') {
        return `Prochain : ${libelleEpisode(s.prochaine_saison, s.prochain_episode)} · ${compte}`;
      }
      return compte;
    }

    function libelleStatut(code) {
      return LIBELLES_STATUT[code] ?? '—';
    }

    function libelleDiffusion(statut) {
      return LIBELLES_DIFFUSION[statut] ?? statut ?? 'Statut de diffusion inconnu';
    }

    // Phrase de la confirmation de retrait, selon le nombre d'épisodes cochés.
    function perteEpisodes(nombre) {
      if (nombre === 0) return "Aucun épisode n'est coché.";
      if (nombre === 1) return "L'épisode coché sera effacé.";
      return `Les ${nombre} épisodes cochés seront effacés.`;
    }

    const statutsManuels = Object.entries(LIBELLES_STATUT)
      .map(([valeur, libelle]) => ({ valeur, libelle }));

    // Ce qui est renvoyé ici est utilisable dans le HTML de #app.
    return {
      utilisateur, chargement, email, motDePasse, erreur, envoiEnCours,
      liste, listeChargee, sections, vusEnCours,
      recherche, resultats, rechercheFaite, rechercheEnCours, ajoutsEnCours, message,
      modeRecherche, rechercheTropCourte, idsDansListe,
      connexion, deconnexion, lancerRecherche, effacerRecherche, ajouter, cocher,
      details, urlAffiche, libelleEpisode, peutCocher, progression,
      route, retourListe, serieIdParTmdb,
      libelleStatut, libelleDiffusion, perteEpisodes, statutsManuels,
      ...ecranFiche, // tout ce que renvoie utiliserFiche() (fiche.js)
    };
  },
}).mount('#app');
