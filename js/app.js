// Point d'entrée de l'application : crée l'application Vue et la relie à
// l'élément #app de index.html.
//
// « ref(...) » crée une variable « réactive » : quand sa valeur change
// (maVariable.value = ...), Vue met à jour tout seul les endroits de la page
// qui l'affichent.

import { createApp, ref, onMounted, onUnmounted } from 'vue';
import {
  utilisateurConnecte,
  surChangementDeConnexion,
  seConnecter,
  seDeconnecter,
} from './base.js';

createApp({
  setup() {
    // --- État de la page ---
    const utilisateur = ref(null);     // utilisateur connecté, ou null
    const chargement = ref(true);      // vrai tant qu'on vérifie la session au démarrage
    const email = ref('');             // champs du formulaire
    const motDePasse = ref('');
    const erreur = ref('');            // message d'erreur à afficher ('' = aucun)
    const envoiEnCours = ref(false);   // vrai pendant la tentative de connexion

    let arreterEcoute = null;

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

    onUnmounted(() => arreterEcoute?.());

    // --- Actions ---
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

    // Ce qui est renvoyé ici est utilisable dans le HTML de #app.
    return {
      utilisateur, chargement, email, motDePasse, erreur, envoiEnCours,
      connexion, deconnexion,
    };
  },
}).mount('#app');
