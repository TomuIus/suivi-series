// Point d'entrée de l'application : crée l'application Vue et la relie à
// l'élément #app de index.html.
//
// Rappels Vue :
// - « ref(...) » crée une variable « réactive » : quand sa valeur change
//   (maVariable.value = ...), Vue met à jour tout seul la page.
// - « computed(...) » crée une valeur calculée à partir d'autres, recalculée
//   automatiquement quand elles changent.
// - « watch(x, ...) » exécute du code à chaque changement de x.

import { createApp, ref, computed, watch, onMounted, onUnmounted } from 'vue';
import {
  utilisateurConnecte,
  surChangementDeConnexion,
  seConnecter,
  seDeconnecter,
  listerSuivis,
  suivreSerie,
} from './base.js';
import { rechercherSeries, importerSerie, urlAffiche } from './tmdb.js';

// Réglages de la recherche
const LONGUEUR_MIN = 2;      // caractères avant de lancer une recherche
const DELAI_FRAPPE = 500;    // ms d'attente après la dernière touche — à revoir après des tests
const DUREE_MESSAGE = 5000;  // ms d'affichage d'un message de confirmation

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
    const liste = ref([]);             // suivis de l'utilisateur, avec la fiche de chaque série
    const listeChargee = ref(false);

    // --- Recherche ---
    const recherche = ref('');            // texte tapé dans la barre
    const resultats = ref([]);            // résultats TMDB affichés
    const rechercheFaite = ref('');       // texte de la dernière recherche aboutie
    const rechercheEnCours = ref(false);
    const ajoutsEnCours = ref(new Set()); // tmdbId des séries en cours d'ajout
    const message = ref('');              // message de confirmation ('' = aucun)

    // On affiche les résultats (à la place de la liste) dès qu'il y a du texte.
    const modeRecherche = computed(() => recherche.value.trim().length > 0);
    const rechercheTropCourte = computed(() => recherche.value.trim().length < LONGUEUR_MIN);
    // Identifiants TMDB des séries déjà suivies, pour afficher « Dans ma liste ».
    const idsDansListe = computed(() => new Set(liste.value.map((s) => s.series?.tmdb_id)));

    let arreterEcoute = null;
    let minuterieFrappe = null;
    let minuterieMessage = null;
    let numeroRecherche = 0; // permet d'ignorer la réponse d'une recherche dépassée

    // --- Au démarrage : y a-t-il déjà une session ouverte ? ---
    onMounted(async () => {
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
      arreterEcoute?.();
      clearTimeout(minuterieFrappe);
      clearTimeout(minuterieMessage);
    });

    // À chaque connexion (nouvel utilisateur), on charge sa liste ;
    // à la déconnexion, on efface de la page tout ce qui le concerne.
    watch(utilisateur, (nouveau, ancien) => {
      if (nouveau && nouveau.id !== ancien?.id) chargerListe();
      if (!nouveau) {
        liste.value = [];
        listeChargee.value = false;
        recherche.value = '';
        message.value = '';
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
      try {
        liste.value = await listerSuivis();
      } catch (e) {
        erreur.value = e.message;
      } finally {
        listeChargee.value = true;
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

    function afficherMessage(texte) {
      message.value = texte;
      clearTimeout(minuterieMessage);
      minuterieMessage = setTimeout(() => { message.value = ''; }, DUREE_MESSAGE);
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

    // Ce qui est renvoyé ici est utilisable dans le HTML de #app.
    return {
      utilisateur, chargement, email, motDePasse, erreur, envoiEnCours,
      liste, listeChargee,
      recherche, resultats, rechercheFaite, rechercheEnCours, ajoutsEnCours, message,
      modeRecherche, rechercheTropCourte, idsDansListe,
      connexion, deconnexion, lancerRecherche, effacerRecherche, ajouter,
      details, urlAffiche,
    };
  },
}).mount('#app');
