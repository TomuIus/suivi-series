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
// local du navigateur sur le site : des réglages propres à cet appareil,
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

    private companion object {
        val CLE_FILTRE_LISTE = stringPreferencesKey("filtre_liste")
    }
}
