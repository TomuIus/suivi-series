package io.github.tomuius.canapilou.donnees

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    /** Déconnexion (la session enregistrée sur le téléphone est effacée). */
    suspend fun seDeconnecter() {
        client.auth.signOut()
    }
}
