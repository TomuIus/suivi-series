package io.github.tomuius.canapilou

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.tomuius.canapilou.donnees.DepotCompte
import io.github.tomuius.canapilou.donnees.DepotListe
import io.github.tomuius.canapilou.donnees.DepotReglages
import io.github.tomuius.canapilou.donnees.DepotSauvegarde
import io.github.tomuius.canapilou.donnees.DepotTmdb

// Assemblage de l'appli (décision 58) : un seul objet crée, au premier usage
// (« by lazy »), le client Supabase et les dépôts. Les écrans demandent leurs
// dépôts ici. Pas de bibliothèque d'injection de dépendances : inutile à cette
// taille.
object Conteneur {
    // Contexte de l'appli (pas d'un écran : il vit aussi longtemps que
    // l'appli), donné par MainActivity au démarrage ; sert à la mémoire sur
    // le téléphone (DataStore).
    private lateinit var contexte: Context

    fun demarrer(contexteAppli: Context) {
        contexte = contexteAppli.applicationContext
    }

    // Le « client » Supabase : l'objet qui envoie les requêtes au serveur et
    // garde la session (vous restez connecté d'une ouverture à l'autre).
    val client: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_CLE_PUBLIQUE) {
            install(Auth)       // connexion
            install(Postgrest)  // lecture et écriture dans la base
            install(Functions)  // fonctions serveur (tmdb)
        }
    }

    val depotCompte: DepotCompte by lazy { DepotCompte(client) }
    val depotListe: DepotListe by lazy { DepotListe(client) }
    val depotTmdb: DepotTmdb by lazy { DepotTmdb(client) }
    val depotReglages: DepotReglages by lazy { DepotReglages(contexte) }
    val depotSauvegarde: DepotSauvegarde by lazy { DepotSauvegarde(client) }
}
