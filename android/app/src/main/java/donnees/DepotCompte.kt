package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Où en est la connexion : l'appli affiche l'écran qui correspond. */
enum class EtatConnexion { CHARGEMENT, CONNECTE, DECONNECTE }

// Dépôt « compte » : tout ce qui touche à la connexion (comme les fonctions
// de connexion de js/base.js sur le site).
class DepotCompte(private val client: SupabaseClient) {

    // État de la connexion, mis à jour tout seul par supabase-kt : au
    // démarrage, il relit la session enregistrée sur le téléphone
    // (« Initializing »), puis signale si l'on est connecté ou non.
    val etat: Flow<EtatConnexion> = client.auth.sessionStatus.map { statut ->
        when (statut) {
            is SessionStatus.Authenticated -> EtatConnexion.CONNECTE
            // La session existe mais n'a pas pu être rafraîchie (souvent : pas
            // de réseau). On reste dans l'appli ; les lectures afficheront
            // une erreur avec « Réessayer ».
            is SessionStatus.RefreshFailure -> EtatConnexion.CONNECTE
            is SessionStatus.NotAuthenticated -> EtatConnexion.DECONNECTE
            else -> EtatConnexion.CHARGEMENT // « Initializing » : session en cours de lecture
        }
    }

    /** Connexion par e-mail et mot de passe. En cas d'échec, lève une exception. */
    suspend fun seConnecter(email: String, motDePasse: String) {
        client.auth.signInWith(Email) {
            this.email = email
            password = motDePasse
        }
    }

    /**
     * Nom affiché en haut de « Ma liste » et sur le Profil, comme nomAffiche
     * sur le site : le nom d'utilisateur choisi dans les Paramètres du site
     * (rangé dans les informations du compte, décision 25), sinon l'e-mail.
     */
    fun nomAffiche(): String = nomUtilisateur()?.takeIf { it.isNotEmpty() } ?: email()

    /** Nom d'utilisateur choisi (null s'il n'y en a pas), pour la page Paramètres. */
    fun nomUtilisateur(): String? {
        val utilisateur = client.auth.currentUserOrNull() ?: return null
        return (utilisateur.userMetadata?.get("nom_utilisateur") as? JsonPrimitive)?.contentOrNull
    }

    /** E-mail du compte connecté (« Connecté avec … » dans les Paramètres). */
    fun email(): String = client.auth.currentUserOrNull()?.email.orEmpty()

    // Augmenté après un changement de nom, pour que « nom » soit recalculé.
    private val versionNom = MutableStateFlow(0)

    /**
     * Nom affiché, qui suit la connexion et les changements faits dans les
     * Paramètres (en-tête de « Ma liste », Profil).
     */
    val nom: Flow<String> = combine(client.auth.sessionStatus, versionNom) { _, _ -> nomAffiche() }

    /**
     * Enregistre le nom d'utilisateur dans les informations du compte
     * (« user_metadata », décision 25), comme changerNomUtilisateur sur le
     * site : vide = pas de nom (l'e-mail s'affiche). supabase-kt met aussi à
     * jour la session gardée sur le téléphone (réglage « updateCurrentUser »,
     * vrai par défaut d'après sa documentation). Ne jamais s'en servir pour
     * des droits : l'utilisateur peut le modifier lui-même. En cas d'échec,
     * lève une exception.
     */
    suspend fun changerNom(nom: String) {
        client.auth.updateUser {
            data { put("nom_utilisateur", nom.ifEmpty { null }) }
        }
        versionNom.update { it + 1 }
    }

    /** Déconnexion (la session enregistrée sur le téléphone est effacée). */
    suspend fun seDeconnecter() {
        client.auth.signOut()
    }
}
