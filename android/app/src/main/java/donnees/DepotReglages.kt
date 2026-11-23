package io.github.tomuius.canapilou.donnees

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Petite mémoire sur le téléphone (décision 88), l'équivalent du stockage
// local du navigateur sur le site (filtre de « Ma liste », thème, mémoire
// des annonces) : des
// réglages propres à cet appareil,
// jamais envoyés à la base. Bibliothèque DataStore « Preferences » de
// Jetpack : des paires clé-valeur, lues comme un Flow (la valeur arrive, puis
// chaque changement).

// Un seul fichier « reglages » pour toute l'appli (le « by » garantit une
// seule instance, comme le demande DataStore).
private val Context.reglages: DataStore<Preferences> by preferencesDataStore(name = "reglages")

class DepotReglages(private val contexte: Context) {

    // Lecture : un fichier illisible (erreur d'entrée-sortie) vaut « aucun
    // réglage » plutôt qu'une appli bloquée ; les autres erreurs remontent.
    private val donnees: Flow<Preferences> = contexte.reglages.data.catch { e ->
        if (e is IOException) {
            Log.w("Canapilou", "Réglages illisibles : ${e.message}")
            emit(emptyPreferences())
        } else {
            throw e
        }
    }

    /**
     * Filtre de « Ma liste » (« tout », « series » ou « animes »), comme la clé
     * « suivi-series.filtre » du site ; null tant qu'aucun choix n'a été fait.
     */
    val filtreListe: Flow<String?> = donnees.map { it[CLE_FILTRE_LISTE] }

    /** Retient le filtre de « Ma liste ». Un échec d'écriture n'est pas grave : le choix ne sera pas retenu. */
    suspend fun enregistrerFiltreListe(valeur: String) {
        try {
            contexte.reglages.edit { it[CLE_FILTRE_LISTE] = valeur }
        } catch (e: IOException) {
            Log.w("Canapilou", "Filtre non retenu : ${e.message}")
        }
    }

    /**
     * Thème choisi (« clair » ou « sombre » ; null = Automatique), comme la
     * clé « suivi-series.theme » du site (décision 48).
     */
    val theme: Flow<String?> = donnees.map { it[CLE_THEME] }

    /** Retient le thème ; « auto » efface le réglage (comme le site). Un échec n'est pas grave. */
    suspend fun enregistrerTheme(valeur: String) {
        try {
            contexte.reglages.edit {
                if (valeur == "clair" || valeur == "sombre") it[CLE_THEME] = valeur else it.remove(CLE_THEME)
            }
        } catch (e: IOException) {
            Log.w("Canapilou", "Thème non retenu : ${e.message}")
        }
    }

    /**
     * Mémoire des annonces des nouveaux badges et rangs (décision 96) pour le
     * compte [utilisateurId], comme la clé « suivi-series.annonces.<identifiant> »
     * du site : lit l'ancienne mémoire (null la première fois) et la remplace
     * par celle que [nouvelle] calcule, d'un seul tenant (deux lectures de la
     * liste qui se croisent ne peuvent pas annoncer deux fois la même chose).
     * Renvoie false si l'écriture a échoué : rien ne sera alors annoncé,
     * sinon l'annonce reviendrait à chaque lecture (comme sur le site).
     */
    suspend fun remplacerMemoireAnnonces(utilisateurId: String, nouvelle: (String?) -> String): Boolean {
        val cle = stringPreferencesKey("annonces_$utilisateurId")
        return try {
            contexte.reglages.edit { it[cle] = nouvelle(it[cle]) }
            true
        } catch (e: IOException) {
            Log.w("Canapilou", "Mémoire des annonces non retenue : ${e.message}")
            false
        }
    }

    private companion object {
        val CLE_FILTRE_LISTE = stringPreferencesKey("filtre_liste")
        val CLE_THEME = stringPreferencesKey("theme")
    }
}
