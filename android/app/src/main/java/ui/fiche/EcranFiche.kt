package io.github.tomuius.canapilou.ui.fiche

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.Saison
import io.github.tomuius.canapilou.ui.commun.AfficheSerie
import io.github.tomuius.canapilou.ui.commun.BarreProgression
import io.github.tomuius.canapilou.ui.commun.DUREE_MESSAGE
import io.github.tomuius.canapilou.ui.commun.Etiquette
import io.github.tomuius.canapilou.ui.commun.afficherPendant
import io.github.tomuius.canapilou.ui.commun.bordurePointillee
import io.github.tomuius.canapilou.ui.liste.libelleEpisode
import io.github.tomuius.canapilou.ui.liste.pourcentage
import kotlinx.coroutines.flow.collectLatest

// Écran « fiche d'une série », partie A (décision 66) : en-tête, résumé,
// épisodes par saison ; un appui coche ou décoche un épisode, puis la barre du
// bas propose de cocher aussi les épisodes précédents. Comme la fiche du site
// (index.html, page « fiche »).

@Composable
fun EcranFiche(
    serieId: Long,
    onRetour: () -> Unit,
    // Un ViewModel propre à cette fiche (Navigation 3 le garde tant qu'elle
    // est dans l'historique).
    vm: FicheViewModel = viewModel { FicheViewModel(serieId, Conteneur.depotListe, Conteneur.depotTmdb) },
) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val titres by vm.titres.collectAsStateWithLifecycle()
    val invite by vm.invite.collectAsStateWithLifecycle()

    // Messages d'erreur en bas de l'écran (un nouveau remplace le précédent).
    val etatMessages = remember { SnackbarHostState() }
    val accessibilite = LocalAccessibilityManager.current
    LaunchedEffect(vm) {
        vm.messages.collectLatest { texte -> etatMessages.afficherPendant(texte, DUREE_MESSAGE, accessibilite) }
    }

    Scaffold(
        topBar = { BarreRetour(onRetour) },
        snackbarHost = { SnackbarHost(etatMessages) },
        bottomBar = {
            invite?.let { episodes ->
                BarreInvite(episodes.size, onOui = vm::cocherPrecedents, onNon = vm::refuserInvite)
            }
        },
    ) { marges ->
        Box(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize(),
        ) {
            when (val e = etat) {
                EtatFiche.Chargement -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is EtatFiche.Erreur -> Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(e.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = { vm.ouvrir() }) { Text("Réessayer") }
                }

                EtatFiche.Introuvable -> Text(
                    "Cette série n'est pas (ou plus) dans votre liste.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    textAlign = TextAlign.Center,
                )

                is EtatFiche.Prete -> ContenuFiche(
                    fiche = e.fiche,
                    titres = titres,
                    onDeplier = vm::chargerTitres,
                    onBasculer = vm::basculer,
                )
            }
        }
    }
}

// Haut de l'écran : « ← Ma liste », comme le bouton du site.
@Composable
private fun BarreRetour(onRetour: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            TextButton(onClick = onRetour) {
                Icon(painterResource(R.drawable.retour), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ma liste")
            }
        }
    }
}

// Barre du bas : « Cocher aussi les 4 épisodes précédents non vus ? ».
@Composable
private fun BarreInvite(nombre: Int, onOui: () -> Unit, onNon: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding() // au-dessus de la barre de navigation du téléphone
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(texteInvite(nombre))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOui) { Text("Oui, les cocher") }
                TextButton(onClick = onNon) { Text("Non") }
            }
        }
    }
}

@Composable
private fun ContenuFiche(
    fiche: Fiche,
    titres: Map<Int, TitresSaison>,
    onDeplier: (Int) -> Unit,
    onBasculer: (Int, Int) -> Unit,
) {
    // Saison dépliée ou repliée, mémorisé même si Android recrée l'écran ;
    // au départ, seule la saison du prochain épisode est dépliée.
    val ouvertes: Map<Int, MutableState<Boolean>> = fiche.saisons.associate { s ->
        s.numero to key(s.numero) { rememberSaveable { mutableStateOf(s.numero == fiche.saisonInitiale) } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "entete") {
            EnTeteFiche(fiche, titres)
        }
        fiche.resume?.takeIf { it.isNotBlank() }?.let { resume ->
            item(key = "resume") { CarteResume(resume) }
        }
        item(key = "episodes") {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Épisodes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Touchez un épisode pour le cocher ou le décocher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (saison in fiche.saisons) {
            val ouverte = ouvertes.getValue(saison.numero)
            item(key = "saison-${saison.numero}") {
                TitreSaison(saison, nbVusSaison(saison, fiche.vus), ouverte.value) {
                    ouverte.value = !ouverte.value
                    if (ouverte.value) onDeplier(saison.numero) // déplier charge les titres
                }
            }
            if (!ouverte.value) continue
            if (saison.nbEpisodes == 0) {
                item(key = "vide-${saison.numero}") {
                    Text(
                        "Aucun épisode connu pour l'instant.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                continue
            }
            val titresSaison = titres[saison.numero]
            items(count = saison.nbEpisodes, key = { i -> "ep-${saison.numero}-${i + 1}" }) { i ->
                val numero = i + 1
                val etatEp = etatEpisode(fiche.serie, fiche.vus, saison.numero, numero)
                val infos = (titresSaison as? TitresSaison.Recus)?.episodes?.get(numero)
                LigneEpisode(
                    numero = numero,
                    titre = titreEpisodeAffiche(infos?.titre, titresSaison is TitresSaison.Chargement, numero),
                    date = texteDate(infos?.date, aVenir = etatEp == EtatEpisode.A_VENIR),
                    etat = etatEp,
                    onBasculer = { onBasculer(saison.numero, numero) },
                )
            }
        }
    }
}

// En-tête : grande affiche, titre, titre original, diffusion et compte,
// barre de progression, prochain épisode.
@Composable
private fun EnTeteFiche(fiche: Fiche, titres: Map<Int, TitresSaison>) {
    val serie = fiche.serie
    val suivi = fiche.suivi
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 92 × 138 dp comme les 92 × 138 pixels du site ; taille TMDB w342
            // pour rester nette sur un téléphone (choix de Claude, à revoir).
            AfficheSerie(serie.afficheChemin, 92.dp, 138.dp, "w342")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        serie.titre,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (suivi.estAnime) Etiquette("Animé")
                }
                serie.titreOriginal?.takeIf { it.isNotEmpty() && it != serie.titre }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    ligneDiffusion(serie, suivi),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pourcentage(suivi.nbVus, suivi.nbDiffuses)?.let { BarreProgression(it) }
                val saison = suivi.prochaineSaison
                val episode = suivi.prochainEpisode
                if (saison != null && episode != null) {
                    // Titre du prochain épisode, dès que sa saison a été chargée
                    // (sur la fiche, le site l'affiche tel quel, même « Épisode 6 »).
                    val titre = (titres[saison] as? TitresSaison.Recus)?.episodes?.get(episode)?.titre
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                append("Prochain : ${libelleEpisode(saison, episode)}")
                            }
                            if (!titre.isNullOrEmpty()) append(" \"$titre\"")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CarteResume(resume: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Résumé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(resume, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Titre d'une saison, « Saison 1 (3 / 10) », avec une flèche ; un appui la
// déplie ou la replie.
@Composable
private fun TitreSaison(saison: Saison, nbVus: Int, ouverte: Boolean, onBasculer: () -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBasculer)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${titreSaison(saison.numero)} ($nbVus / ${saison.nbEpisodes})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.chevron),
                contentDescription = if (ouverte) "Replier la saison" else "Déplier la saison",
                modifier = Modifier.rotate(if (ouverte) 180f else 0f),
            )
        }
    }
}

// Un épisode : case, numéro, titre et date. Toute la ligne se touche (grande
// zone d'appui) ; les lecteurs d'écran l'annoncent comme une case à cocher.
// Pas encore sorti : contour en pointillé, pas cochable.
@Composable
private fun LigneEpisode(numero: Int, titre: String, date: String, etat: EtatEpisode, onBasculer: () -> Unit) {
    val couleurs = MaterialTheme.colorScheme
    val forme = RoundedCornerShape(8.dp)
    val aVenir = etat == EtatEpisode.A_VENIR
    val contour = if (aVenir) {
        Modifier.bordurePointillee(couleurs.outline, 8.dp)
    } else {
        Modifier
            .background(couleurs.surface, forme)
            .border(1.dp, couleurs.outline, forme)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(contour)
            .clip(forme)
            .toggleable(
                value = etat == EtatEpisode.VU,
                enabled = !aVenir,
                role = Role.Checkbox,
                onValueChange = { onBasculer() },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CaseEpisode(etat)
        Text(
            "$numero",
            fontWeight = FontWeight.SemiBold,
            color = couleurs.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(titre, color = if (etat == EtatEpisode.NON_VU) couleurs.onSurface else couleurs.onSurfaceVariant)
            if (date.isNotEmpty()) {
                Text(date, style = MaterialTheme.typography.bodySmall, color = couleurs.onSurfaceVariant)
            }
        }
    }
}

// Case dessinée : remplie de la couleur principale avec une coche si l'épisode
// est vu, en pointillé s'il n'est pas encore sorti.
@Composable
private fun CaseEpisode(etat: EtatEpisode) {
    val couleurs = MaterialTheme.colorScheme
    val forme = RoundedCornerShape(6.dp)
    val modificateur = when (etat) {
        EtatEpisode.VU -> Modifier.background(couleurs.primary, forme)
        EtatEpisode.NON_VU -> Modifier.border(2.dp, couleurs.outline, forme)
        EtatEpisode.A_VENIR -> Modifier.bordurePointillee(couleurs.outline, 6.dp, epaisseur = 2.dp)
    }
    Box(
        modifier = Modifier
            .size(24.dp)
            .then(modificateur),
        contentAlignment = Alignment.Center,
    ) {
        if (etat == EtatEpisode.VU) {
            Icon(
                painterResource(R.drawable.coche),
                contentDescription = null,
                tint = couleurs.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
