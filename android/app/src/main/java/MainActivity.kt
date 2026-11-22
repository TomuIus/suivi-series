package io.github.tomuius.canapilou

// ÉCRAN D'ESSAI JETABLE (décision 56) : vérifie que la bibliothèque supabase-kt
// sait se connecter avec la clé publishable, lire nos vues et appeler la
// fonction serveur « tmdb ». Il sera remplacé par les vrais écrans.
// Le mot de passe est tapé dans l'appli par l'utilisateur ; il n'est écrit
// nulle part dans le code et n'est pas affiché.

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.tomuius.canapilou.ui.theme.CanapilouTheme
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Le « client » Supabase : l'objet qui envoie les requêtes au serveur et garde
// la session. Créé une seule fois, au premier usage (« by lazy »).
val supabase by lazy {
    createSupabaseClient(supabaseUrl = SUPABASE_URL, supabaseKey = SUPABASE_CLE_PUBLIQUE) {
        install(Auth)       // connexion
        install(Postgrest)  // lecture et écriture dans la base
        install(Functions)  // fonctions serveur (tmdb)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CanapilouTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    EcranEssai(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun EcranEssai(modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var enCours by remember { mutableStateOf(false) }
    val resultats = remember { mutableStateListOf<String>() }
    val portee = rememberCoroutineScope()

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Essai de connexion à Supabase", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = motDePasse,
            onValueChange = { motDePasse = it },
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = !enCours && email.isNotBlank() && motDePasse.isNotEmpty(),
            onClick = {
                enCours = true
                resultats.clear()
                portee.launch {
                    lancerEssais(email.trim(), motDePasse) { ligne -> resultats.add(ligne) }
                    motDePasse = "" // on n'en garde pas trace à l'écran
                    enCours = false
                }
            },
        ) {
            Text(if (enCours) "Essai en cours…" else "Se connecter et tester")
        }
        resultats.forEach { ligne -> Text(ligne) }
    }
}

// Les quatre vérifications, l'une après l'autre. Chaque résultat (réussite ou
// message d'erreur) est transmis à « noter » pour être affiché.
suspend fun lancerEssais(email: String, motDePasse: String, noter: (String) -> Unit) {
    // 1. Connexion avec l'e-mail et le mot de passe (clé publishable).
    try {
        supabase.auth.signInWith(Email) {
            this.email = email
            password = motDePasse
        }
        noter("1. Connexion : OK")
    } catch (e: Exception) {
        noter("1. Connexion : échec — ${e.message}")
        return // inutile d'aller plus loin sans connexion
    }

    // 2. Lecture de la vue « ma_liste » (vos séries). Chaque ligne est lue
    // telle quelle en JSON : pas besoin de décrire ses colonnes pour l'essai.
    var premiere: JsonObject? = null
    try {
        val liste = supabase.from("ma_liste").select().decodeList<JsonObject>()
        premiere = liste.firstOrNull()
        noter("2. Lecture de « ma_liste » : OK, ${liste.size} série(s)")
    } catch (e: Exception) {
        noter("2. Lecture de « ma_liste » : échec — ${e.message}")
    }

    // 3. Lecture de la vue « ma_progression » (une seule ligne).
    try {
        val progression = supabase.from("ma_progression").select().decodeSingle<JsonObject>()
        val niveau = progression["niveau"]?.jsonPrimitive?.contentOrNull
        val points = progression["points"]?.jsonPrimitive?.contentOrNull
        noter("3. Lecture de « ma_progression » : OK, niveau $niveau, $points points")
    } catch (e: Exception) {
        noter("3. Lecture de « ma_progression » : échec — ${e.message}")
    }

    // 4. Appel de la fonction serveur « tmdb » (action « saison », comme le
    // site pour les titres d'épisodes), sur la première série de la liste.
    val tmdbId = premiere?.get("tmdb_id")?.jsonPrimitive?.intOrNull
    if (premiere == null || tmdbId == null) {
        noter("4. Fonction « tmdb » : pas essayée (aucune série lue)")
        return
    }
    val titre = premiere["titre"]?.jsonPrimitive?.contentOrNull
    val saison = premiere["prochaine_saison"]?.jsonPrimitive?.intOrNull ?: 1
    try {
        val reponse = supabase.functions.invoke(
            function = "tmdb",
            body = buildJsonObject {
                put("action", "saison")
                put("tmdbId", tmdbId)
                put("saison", saison)
            },
        )
        val episodes = Json.parseToJsonElement(reponse.bodyAsText()).jsonObject["episodes"]?.jsonArray
        val premierTitre = episodes?.firstOrNull()?.jsonObject?.get("titre")?.jsonPrimitive?.contentOrNull
        noter(
            "4. Fonction « tmdb » : OK, « $titre » saison $saison : ${episodes?.size} épisode(s), " +
                "le premier s'appelle « $premierTitre »"
        )
    } catch (e: Exception) {
        noter("4. Fonction « tmdb » : échec — ${e.message}")
    }
}
