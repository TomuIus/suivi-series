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
  changerNomUtilisateur,
  listerSuivis,
  suivreSerie,
  marquerVu,
  annulerVu,
  lireDonneesPourExport,
} from './base.js';
import { rechercherSeries, importerSerie, urlAffiche } from './tmdb.js';
import { utiliserFiche } from './fiche.js';

// Réglages de la recherche
const LONGUEUR_MIN = 2;      // caractères avant de lancer une recherche
const DELAI_FRAPPE = 500;    // ms d'attente après la dernière touche — à revoir après des tests
const DUREE_MESSAGE = 5000;  // ms d'affichage d'un message de confirmation
const DUREE_ANNULATION = 8000; // ms pendant lesquels « Annuler » reste proposé

// Sections de « Ma liste », dans l'ordre d'affichage, avec les statuts de la
// base que chacune regroupe. Les sections « repliées » sont fermées au départ
// (on les ouvre d'un clic sur leur titre).
// « En cours » regroupe aussi « À voir » (décision 27) : la base garde la
// différence (commencée ou non), seul l'affichage les réunit.
// « Plus tard » (décision 26) : tout en bas, repliée, pour garder de côté les
// séries qu'on compte voir un jour sans encombrer celles qu'on regarde.
const SECTIONS = [
  { cle: 'en_cours',  statuts: ['en_cours', 'a_voir'], titre: 'En cours',  repliee: false },
  { cle: 'a_jour',    statuts: ['a_jour'],             titre: 'À jour',    repliee: false },
  { cle: 'termine',   statuts: ['termine'],            titre: 'Terminé',   repliee: true },
  { cle: 'abandonne', statuts: ['abandonne'],          titre: 'Abandonné', repliee: true },
  { cle: 'plus_tard', statuts: ['plus_tard'],          titre: 'Plus tard', repliee: true },
];

// Filtre « Tout · Séries · Animés » de « Ma liste » (décision 23). Le choix
// est retenu dans le navigateur (localStorage) d'une visite à l'autre.
const FILTRES = [
  { valeur: 'tout',   libelle: 'Tout' },
  { valeur: 'series', libelle: 'Séries' },
  { valeur: 'animes', libelle: 'Animés' },
];
const CLE_FILTRE = 'suivi-series.filtre';

function lireFiltre() {
  try {
    const valeur = localStorage.getItem(CLE_FILTRE);
    return FILTRES.some((f) => f.valeur === valeur) ? valeur : 'tout';
  } catch {
    return 'tout'; // stockage indisponible (navigation privée…)
  }
}

function enregistrerFiltre(valeur) {
  try {
    localStorage.setItem(CLE_FILTRE, valeur);
  } catch {
    // Pas grave : le choix ne sera simplement pas retenu.
  }
}

const LONGUEUR_MAX_NOM = 30; // caractères pour le nom d'utilisateur

// Libellés affichés pour les statuts (codes de la base) et pour les statuts
// de diffusion de TMDB. « a_voir » (pas encore commencée) s'affiche
// « En cours » depuis la décision 27.
const LIBELLES_STATUT = {
  a_voir: 'En cours',
  en_cours: 'En cours',
  plus_tard: 'Plus tard',
  a_jour: 'À jour',
  termine: 'Terminé',
  abandonne: 'Abandonné',
};
// Choix proposés dans le menu « Statut » de la fiche, dans cet ordre
// (« À voir » n'y est plus : il est réuni avec « En cours »).
const STATUTS_MANUELS = ['en_cours', 'plus_tard', 'a_jour', 'termine', 'abandonne'];
const LIBELLES_DIFFUSION = {
  'Returning Series': 'En diffusion',
  'In Production': 'En production',
  'Planned': 'Prévue',
  'Pilot': 'Pilote',
  'Ended': 'Terminée',
  'Canceled': 'Annulée',
};

// Quelle page afficher, d'après l'adresse :
//   « #/serie/42 »    = fiche de la série 42 du catalogue (identifiant interne) ;
//   « #/apercu/1399 » = aperçu d'une série TMDB pas encore ajoutée ;
//   toute autre adresse = « Ma liste ».
// Passer par l'adresse permet au bouton retour du navigateur (ou au geste
// retour du téléphone) de marcher.
function lireRoute() {
  let trouve = /^#\/serie\/(\d+)$/.exec(location.hash);
  if (trouve) return { page: 'fiche', serieId: Number(trouve[1]) };
  trouve = /^#\/apercu\/(\d+)$/.exec(location.hash);
  if (trouve) return { page: 'fiche', tmdbId: Number(trouve[1]) };
  return { page: 'liste' };
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

    // --- Filtre séries / animés ---
    const filtre = ref(lireFiltre());
    watch(filtre, enregistrerFiltre);

    // Nombre de séries dans chaque filtre, affiché sur les boutons.
    const comptes = computed(() => {
      const animes = liste.value.filter((s) => s.est_anime).length;
      return { tout: liste.value.length, series: liste.value.length - animes, animes };
    });

    const listeFiltree = computed(() => {
      if (filtre.value === 'animes') return liste.value.filter((s) => s.est_anime);
      if (filtre.value === 'series') return liste.value.filter((s) => !s.est_anime);
      return liste.value;
    });

    // La liste (filtrée) découpée en sections ; les sections vides ne sont pas
    // affichées. L'ordre dans chaque section vient de la base : activité la
    // plus récente d'abord.
    const sections = computed(() =>
      SECTIONS
        .map((section) => ({ ...section, series: listeFiltree.value.filter((s) => section.statuts.includes(s.statut)) }))
        .filter((section) => section.series.length > 0)
    );

    // --- Mon compte : statistiques (décision 28) ---
    // Calculées dans le navigateur à partir de la liste déjà chargée (aucune
    // requête en plus) ; elles se mettent à jour d'elles-mêmes quand on coche
    // un épisode. Une ligne par section de « Ma liste » (même regroupement),
    // trois colonnes : Total, Séries, Animés. Le filtre n'a pas d'effet ici.
    const statistiques = computed(() => {
      const groupes = [
        liste.value,
        liste.value.filter((s) => !s.est_anime),
        liste.value.filter((s) => s.est_anime),
      ];
      const pourChaque = (calcul) => groupes.map(calcul);
      return [
        { libelle: 'Titres dans ma liste', forte: true, valeurs: pourChaque((g) => g.length) },
        ...SECTIONS.map((section) => ({
          libelle: section.titre,
          valeurs: pourChaque((g) => g.filter((s) => section.statuts.includes(s.statut)).length),
        })),
        // nb_vus vient de la vue « ma_liste » : épisodes spéciaux non comptés.
        { libelle: 'Épisodes vus', forte: true, valeurs: pourChaque((g) => g.reduce((n, s) => n + s.nb_vus, 0)) },
      ];
    });

    // « 2 400 » plutôt que « 2400 »
    function nombre(n) {
      return n.toLocaleString('fr-FR');
    }

    // --- Mon compte (nom d'utilisateur) ---
    const compteOuvert = ref(false);
    const nomSaisi = ref('');
    const enregistrementNom = ref(false);
    // Nom affiché en haut : le nom d'utilisateur s'il existe, sinon l'e-mail.
    const nomAffiche = computed(() =>
      utilisateur.value?.user_metadata?.nom_utilisateur || utilisateur.value?.email || '');

    // --- Recherche ---
    const recherche = ref('');            // texte tapé dans la barre
    const resultats = ref([]);            // résultats TMDB affichés
    const rechercheFaite = ref('');       // texte de la dernière recherche aboutie
    const rechercheEnCours = ref(false);
    const ajoutsEnCours = ref(new Map()); // tmdbId → 'liste' ou 'plus_tard' (bouton appuyé)
    const message = ref(null);            // confirmation : { texte, annuler } ou null

    // --- Sauvegarde ---
    const exportEnCours = ref(false);

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
      // Série ajoutée depuis l'aperçu : on affiche sa fiche « suivie ».
      // location.replace remplace l'aperçu dans l'historique : le retour
      // ramène donc aux résultats de recherche, pas à l'aperçu.
      apresAjout: async (serieId, titre, plusTard) => {
        afficherMessage(messageAjout(titre, true, plusTard));
        await chargerListe();
        if (route.value.serieId === serieId) ecranFiche.ouvrirFiche({ serieId });
        else location.replace(`#/serie/${serieId}`);
      },
    });

    // Série interne correspondant à un identifiant TMDB (pour le lien
    // « voir la fiche » dans les résultats de recherche).
    const serieIdParTmdb = computed(() => new Map(liste.value.map((s) => [s.tmdb_id, s.serie_id])));
    const statutParTmdb = computed(() => new Map(liste.value.map((s) => [s.tmdb_id, s.statut])));

    // Mention d'un résultat de recherche déjà dans la liste.
    function mentionDansListe(tmdbId) {
      return statutParTmdb.value.get(tmdbId) === 'plus_tard' ? '✓ Dans « Plus tard »' : '✓ Dans ma liste';
    }

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
        if (route.value.page === 'fiche') ecranFiche.ouvrirFiche(route.value);
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
        // Aperçu d'une série qu'on suit déjà : on va directement à sa fiche.
        const dejaSuivie = r.tmdbId != null ? serieIdParTmdb.value.get(r.tmdbId) : undefined;
        if (dejaSuivie != null) {
          location.replace(`#/serie/${dejaSuivie}`);
          return;
        }
        window.scrollTo(0, 0);
        ecranFiche.ouvrirFiche(r);
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
        completerClassement(lignes);
      } catch (e) {
        erreur.value = e.message;
      } finally {
        listeChargee.value = true;
      }
    }

    // Les séries ajoutées avant la séparation séries / animés n'ont pas encore
    // de classement automatique (anime_detecte vide) : on fait remettre leur
    // fiche à jour par la fonction serveur, une par une, en arrière-plan, une
    // seule fois par visite. Ensuite, on recharge la liste.
    let classementLance = false;
    async function completerClassement(lignes) {
      const aClasser = lignes.filter((s) => s.anime_detecte == null);
      if (classementLance || aClasser.length === 0) return;
      classementLance = true;
      for (const s of aClasser) {
        try {
          await importerSerie(s.tmdb_id);
        } catch (e) {
          console.warn(`Classement de « ${s.titre} » impossible :`, e.message);
        }
      }
      chargerListe();
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

    // Boutons « Ajouter » (plusTard = false) et « Plus tard » (plusTard = true)
    // d'un résultat de recherche.
    async function ajouter(resultat, plusTard = false) {
      const id = resultat.tmdbId;
      if (ajoutsEnCours.value.has(id)) return; // évite un double clic
      ajoutsEnCours.value.set(id, plusTard ? 'plus_tard' : 'liste');
      erreur.value = '';
      try {
        const serie = await importerSerie(id);                        // 1. fiche copiée dans le catalogue
        const nouvelle = await suivreSerie(serie.id, { plusTard });   // 2. série ajoutée à MA liste
        await chargerListe();
        afficherMessage(messageAjout(serie.titre, nouvelle, plusTard));
      } catch (e) {
        erreur.value = e.message;
      } finally {
        ajoutsEnCours.value.delete(id);
      }
    }

    // Texte de confirmation après un ajout.
    function messageAjout(titre, nouvelle, plusTard) {
      if (!nouvelle) return `« ${titre} » était déjà dans votre liste.`;
      return plusTard
        ? `« ${titre} » est maintenant dans « Plus tard ».`
        : `« ${titre} » est maintenant dans votre liste.`;
    }

    // Affiche une confirmation ; si « annuler » est fourni, un bouton
    // « Annuler » l'accompagne, un peu plus longtemps.
    function afficherMessage(texte, annuler = null) {
      message.value = { texte, annuler };
      clearTimeout(minuterieMessage);
      minuterieMessage = setTimeout(() => { message.value = null; },
        annuler ? DUREE_ANNULATION : DUREE_MESSAGE);
    }

    // --- Actions : mon compte ---

    function ouvrirCompte() {
      nomSaisi.value = utilisateur.value?.user_metadata?.nom_utilisateur ?? '';
      compteOuvert.value = true;
    }

    function fermerCompte() {
      compteOuvert.value = false;
    }

    async function enregistrerNom() {
      const nom = nomSaisi.value.trim();
      if (nom.length > LONGUEUR_MAX_NOM) {
        erreur.value = `Le nom doit faire au plus ${LONGUEUR_MAX_NOM} caractères.`;
        return;
      }
      enregistrementNom.value = true;
      erreur.value = '';
      try {
        utilisateur.value = await changerNomUtilisateur(nom);
        compteOuvert.value = false;
        afficherMessage(nom ? `Vous vous appelez maintenant « ${nom} ».` : 'Nom retiré : votre e-mail est de nouveau affiché.');
      } catch (e) {
        erreur.value = e.message;
      } finally {
        enregistrementNom.value = false;
      }
    }

    // --- Actions : sauvegarde ---

    // Télécharge un fichier JSON avec toute la liste et les épisodes cochés.
    async function exporter() {
      if (exportEnCours.value) return;
      exportEnCours.value = true;
      erreur.value = '';
      try {
        const series = await lireDonneesPourExport();
        const sauvegarde = {
          format: 'suivi-series',      // pour reconnaître le fichier lors d'un futur import
          version: 1,
          exporte_le: new Date().toISOString(),
          series,
        };
        telecharger(`suivi-series-${dateDuJour()}.json`, JSON.stringify(sauvegarde, null, 2));
        const nbEpisodes = series.reduce((total, s) => total + s.episodes_vus.length, 0);
        afficherMessage(`Sauvegarde téléchargée : ${pluriel(series.length, 'série')}, `
          + `${pluriel(nbEpisodes, 'épisode coché', 'épisodes cochés')}.`);
      } catch (e) {
        erreur.value = e.message;
      } finally {
        exportEnCours.value = false;
      }
    }

    // Fait télécharger un texte comme fichier : on crée un lien invisible vers
    // le contenu (« Blob ») et on clique dessus.
    function telecharger(nomFichier, contenu) {
      const adresse = URL.createObjectURL(new Blob([contenu], { type: 'application/json' }));
      const lien = document.createElement('a');
      lien.href = adresse;
      lien.download = nomFichier;
      document.body.appendChild(lien);
      lien.click();
      lien.remove();
      setTimeout(() => URL.revokeObjectURL(adresse), 1000); // libère la mémoire
    }

    // « 2026-11-13 », à l'heure locale (pour le nom du fichier).
    function dateDuJour() {
      const d = new Date();
      const deux = (n) => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${deux(d.getMonth() + 1)}-${deux(d.getDate())}`;
    }

    // « 1 série », « 3 séries »
    function pluriel(nombre, singulier, formePlurielle = `${singulier}s`) {
      return `${nombre} ${nombre > 1 ? formePlurielle : singulier}`;
    }

    // --- Aides pour l'affichage ---

    // Pourcentage d'épisodes vus parmi les épisodes déjà sortis (spéciaux exclus,
    // comme dans la vue « ma_liste »). Arrondi vers le bas : 100 % seulement
    // quand tout est vu. null s'il n'y a encore aucun épisode sorti.
    function pourcentage(s) {
      if (!s || !(s.nb_diffuses > 0)) return null;
      return Math.floor((100 * Math.min(s.nb_vus, s.nb_diffuses)) / s.nb_diffuses);
    }
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
    // et seulement dans la section « En cours » (statuts en_cours et a_voir).
    function peutCocher(s) {
      return s.prochaine_saison != null && (s.statut === 'en_cours' || s.statut === 'a_voir');
    }

    // Ligne d'informations sous le titre d'une série de la liste.
    function progression(s) {
      const compte = `${s.nb_vus} / ${s.nb_diffuses} épisodes vus`;
      if (s.statut === 'termine') return `${s.nb_vus} épisodes vus`;
      if (s.statut === 'a_jour') return `${s.nb_vus} épisodes vus · en attente de la suite`;
      if (s.nb_diffuses === 0) return "Aucun épisode diffusé pour l'instant";
      if (s.statut === 'plus_tard') return compte; // pas de « Prochain » : mise de côté
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

    // « 3 saisons » (sans compter les épisodes spéciaux)
    function nombreSaisons(saisons) {
      const n = saisons.filter((s) => s.numero > 0).length;
      if (n === 0) return 'Aucune saison annoncée';
      return n === 1 ? '1 saison' : `${n} saisons`;
    }

    // Lien d'un résultat de recherche : la fiche si la série est dans la
    // liste, sinon son aperçu.
    function lienResultat(r) {
      const serieId = serieIdParTmdb.value.get(r.tmdbId);
      return serieId != null ? `#/serie/${serieId}` : `#/apercu/${r.tmdbId}`;
    }

    // Phrase de la confirmation de retrait, selon le nombre d'épisodes cochés.
    function perteEpisodes(nombre) {
      if (nombre === 0) return "Aucun épisode n'est coché.";
      if (nombre === 1) return "L'épisode coché sera effacé.";
      return `Les ${nombre} épisodes cochés seront effacés.`;
    }

    const statutsManuels = STATUTS_MANUELS
      .map((valeur) => ({ valeur, libelle: LIBELLES_STATUT[valeur] }));

    // Valeur affichée dans le menu « Statut » : un ancien statut manuel
    // « À voir » apparaît comme « En cours » (même section depuis la décision 27).
    function valeurStatutManuel(suivi) {
      const valeur = suivi.statut_manuel ?? '';
      return valeur === 'a_voir' ? 'en_cours' : valeur;
    }

    // Ce qui est renvoyé ici est utilisable dans le HTML de #app.
    return {
      utilisateur, chargement, email, motDePasse, erreur, envoiEnCours,
      liste, listeChargee, sections, vusEnCours, exportEnCours, exporter, pourcentage,
      recherche, resultats, rechercheFaite, rechercheEnCours, ajoutsEnCours, message,
      modeRecherche, rechercheTropCourte, idsDansListe,
      connexion, deconnexion, lancerRecherche, effacerRecherche, ajouter, cocher,
      details, urlAffiche, libelleEpisode, peutCocher, progression,
      route, retourListe, serieIdParTmdb, lienResultat, nombreSaisons, mentionDansListe,
      libelleStatut, libelleDiffusion, perteEpisodes, statutsManuels, valeurStatutManuel,
      filtres: FILTRES, filtre, comptes, listeFiltree,
      compteOuvert, nomSaisi, enregistrementNom, nomAffiche, ouvrirCompte, fermerCompte, enregistrerNom,
      statistiques, nombre,
      longueurMaxNom: LONGUEUR_MAX_NOM,
      ...ecranFiche, // tout ce que renvoie utiliserFiche() (fiche.js)
    };
  },
}).mount('#app');
