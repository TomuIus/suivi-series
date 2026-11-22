package io.github.tomuius.canapilou.donnees

import java.io.IOException

/**
 * Transforme une erreur en message compréhensible, en français. Mêmes cas que
 * traduireErreur dans js/base.js (codes renvoyés par Supabase pour la
 * connexion), plus l'absence de réseau. Le code d'erreur est cherché dans le
 * texte du message (forme exacte des messages de supabase-kt non vérifiée :
 * à revoir après des tests). Sinon, on affiche le message d'origine.
 */
fun messageErreur(e: Throwable): String {
    val texte = (e.message ?: "").lowercase()
    return when {
        "invalid_credentials" in texte || "invalid login credentials" in texte ->
            "E-mail ou mot de passe incorrect."
        "email_not_confirmed" in texte || "email not confirmed" in texte ->
            "Cette adresse e-mail n'a pas encore été confirmée."
        "over_request_rate_limit" in texte || "rate limit" in texte ->
            "Trop de tentatives. Patientez quelques minutes avant de réessayer."
        e is IOException || e::class.simpleName == "HttpRequestException" ->
            "Impossible de joindre le serveur. Vérifiez votre connexion internet."
        else -> "Erreur : ${e.message ?: e::class.simpleName}"
    }
}
