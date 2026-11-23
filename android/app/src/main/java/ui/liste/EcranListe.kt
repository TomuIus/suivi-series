package io.github.tomuius.canapilou.ui.liste

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomuius.canapilou.Conteneur
import io.github.tomuius.canapilou.R
import io.github.tomuius.canapilou.donnees.ResultatTmdb
import io.github.tomuius.canapilou.donnees.SerieSuivie
import io.github.tomuius.canapilou.ui.commun.AfficheSerie
import io.github.tomuius.canapilou.ui.commun.BarreProgression
import io.github.tomuius.canapilou.ui.commun.DUREE_ANNULATION
import io.github.tomuius.canapilou.ui.commun.DUREE_MESSAGE
import io.github.tomuius.canapilou.ui.commun.Etiquette
import io.github.tomuius.canapilou.ui.commun.afficherPendant
import io.github.tomuius.canapilou.ui.fiche.libelleStatut
import kotlinx.coroutines.flow.collectLatest

// Écran « Ma liste » : vos séries, rangées en sections par statut, comme sur
// le site : affiches, titre du prochain épisode, bouton « Vu » avec
// « Annuler », menu (Actualiser, À propos, Se déconnecter) ; un appui sur une
// série ouvre sa fiche (décision 66). Barre de recherche toujours en haut
// (décision 75) : dès qu'on tape, les résultats remplacent la liste, d'abord
// « Dans ma liste », puis « Sur TMDB » avec « Ajouter » et « Plus tard »
// (décisions 44 et 76).

@Composable
fun EcranListe(
    onOuvrirSerie: (Long) -> Unit,
    // Aperçu d'une série de TMDB qui n'est pas dans la liste (identifiant TMDB).
    onApercu: (Int) -> Unit,
    onAPropos: () -> Unit,
    // Message à afficher en arrivant (série retirée depuis sa fiche), puis oublié.
    messageEnAttente: String?,
    onMessagePris: () -> Unit,
    // Un ViewModel propre à cet écran (Navigation 3 le garde tant que l'écran
    // est dans l'historique), créé avec les dépôts du Conteneur.
    vm: ListeViewModel = viewModel {
        ListeViewModel(Conteneur.depotListe, Conteneur.depotCompte, Conteneur.depotTmdb)
    },
) {
    val etat by vm.etat.collectAsStateWithLifecycle()
    val titres by vm.titres.collectAsStateWithLifecycle()
    val vusEnCours by vm.vusEnCours.collectAsStateWithLifecycle()
    val rechercheTmdb by vm.rechercheTmdb.collectAsStateWithLifecycle()
    val ajoutsEnCours by vm.ajoutsEnCours.collectAsStateWithLifecycle()
    val modeRecherche = vm.recherche.isNotBlank()

    // Position de la liste et sections dépliées, gardées ici (et non dans la
    // liste elle-même) pour les retrouver après une recherche. Mémorisées même
    // si Android recrée l'écran ; « key » donne à chaque section sa case.
    val defilementListe = rememberLazyListState()
    val ouvertes: Map<Section, MutableState<Boolean>> = Section.entries.associateWith { section ->
        key(section.name) { rememberSaveable { mutableStateOf(!section.replieeParDefaut) } }
    }

    // Pendant une recherche, le retour du téléphone l'efface (comme Échap
    // sur le site) au lieu de quitter l'appli.
    BackHandler(enabled = modeRecherche) { vm.effacerRecherche() }

    // Messages en bas de l'écran (« snackbar »). « collectLatest » : un
    // nouveau message remplace aussitôt le précédent, comme sur le site.
    val etatMessages = remember { SnackbarHostState() }
    val accessibilite = LocalAccessibilityManager.current
    LaunchedEffect(vm) {
        vm.messages.collectLatest { m ->
            val resultat = if (m.annulation != null) {
                etatMessages.afficherPendant(m.texte, DUREE_ANNULATION, accessibilite, action = "Annuler")
            } else {
                etatMessages.afficherPendant(m.texte, DUREE_MESSAGE, accessibilite)
            }
            if (resultat == SnackbarResult.ActionPerformed && m.annulation != null) vm.annuler(m.annulation)
        }
    }

    // De retour d'une fiche où l'on a coché des épisodes : la liste se relit
    // (sans l'indicateur d'attente), comme le site au retour sur « Ma liste ».
    LaunchedEffect(vm) { vm.relireSiPerimee() }
    LaunchedEffect(messageEnAttente) {
        if (messageEnAttente != null) {
            vm.annoncer(messageEnAttente)
            onMessagePris()
        }
    }

    Scaffold(
        topBar = {
            EnTete(
                onActualiser = { vm.charger(oublierTitres = true) },
                onAPropos = onAPropos,
                onDeconnexion = { vm.seDeconnecter() },
            )
        },
        snackbarHost = { SnackbarHost(etatMessages) },
    ) { marges ->
        Column(
            modifier = Modifier
                .padding(marges)
                .fillMaxSize(),
        ) {
            BarreRecherche(
                texte = vm.recherche,
                onChange = vm::changerRecherche,
                onRechercher = vm::lancerRecherche,
                onEffacer = vm::effacerRecherche,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (modeRecherche) {
                    ResultatsRecherche(
                        texte = vm.recherche,
                        series = (etat as? EtatListe.Pret)?.series.orEmpty(),
                        rechercheTmdb = rechercheTmdb,
                        ajoutsEnCours = ajoutsEnCours,
                        titres = titres,
                        vusEnCours = vusEnCours,
                        onVu = vm::cocher,
                        onOuvrir = onOuvrirSerie,
                        onApercu = onApercu,
                        onAjouter = vm::ajouter,
                    )
                } else when (val e = etat) {
                    EtatListe.Chargement ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    is EtatListe.Erreur -> Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(e.message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        Button(onClick = { vm.charger() }) { Text("Réessayer") }
                    }

                    is EtatListe.Pret ->
                        if (e.total == 0) {
                            Text(
                                "Votre liste est vide pour l'instant.",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            ListeEnSections(
                                e.sections, titres, vusEnCours, ouvertes, defilementListe,
                                onVu = vm::cocher, onOuvrir = onOuvrirSerie,
                            )
                        }
                }
            }
        }
    }
}

// Barre de recherche : loupe, texte, croix pour effacer. La touche
// « Rechercher » du clavier lance la recherche tout de suite (Entrée sur le site).
@Composable
private fun BarreRecherche(
    texte: String,
    onChange: (String) -> Unit,
    onRechercher: () -> Unit,
    onEffacer: () -> Unit,
) {
    val clavier = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = texte,
        onValueChange = onChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp),
        placeholder = { Text("Rechercher une série ou un animé…") },
        leadingIcon = { Icon(painterResource(R.drawable.loupe), contentDescription = null) },
        trailingIcon = if (texte.isEmpty()) null else {
            {
                IconButton(onClick = onEffacer) {
                    Icon(painterResource(R.drawable.croix), contentDescription = "Effacer la recherche")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            onRechercher()
            clavier?.hide()
        }),
    )
}

// Résultats de la recherche, à la place de la liste : d'abord vos séries qui
// correspondent (mêmes cartes que dans la liste, avec leur statut), puis les
// résultats de TMDB sans celles-ci (recherche commune, décision 44).
@Composable
private fun ResultatsRecherche(
    texte: String,
    series: List<SerieSuivie>,
    rechercheTmdb: RechercheTmdb,
    ajoutsEnCours: Map<Int, String>,
    titres: Map<CleSaison, Map<Int, String?>>,
    vusEnCours: Set<Long>,
    onVu: (SerieSuivie) -> Unit,
    onOuvrir: (Long) -> Unit,
    onApercu: (Int) -> Unit,
    onAjouter: (ResultatTmdb, Boolean) -> Unit,
) {
    val tropCourte = texte.trim().length < LONGUEUR_MIN
    val dansListe = remember(series, texte) { if (tropCourte) emptyList() else chercherDansListe(series, texte) }
    val autres = remember(rechercheTmdb, dansListe) { autresResultats(rechercheTmdb.resultats, dansListe) }
    // Séries suivies, par identifiant TMDB : mention « Dans ma liste » et
    // ouverture de leur fiche depuis les résultats TMDB.
    val suivies = remember(series) { series.associateBy { it.tmdbId } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (tropCourte) {
            item(key = "court") { TexteInfo("Tapez au moins 2 caractères.") }
            return@LazyColumn
        }
        if (dansListe.isNotEmpty()) {
            item(key = "titre-liste") { TitreBloc("Dans ma liste (${dansListe.size})") }
            items(dansListe, key = { "liste-${it.serieId}" }) { serie ->
                CarteSerie(
                    s = serie,
                    titreProchain = titreProchain(serie, titres),
                    vuEnCours = serie.serieId in vusEnCours,
                    onVu = { onVu(serie) },
                    onOuvrir = { onOuvrir(serie.serieId) },
                    statutVisible = true,
                )
            }
            item(key = "titre-tmdb") { TitreBloc("Sur TMDB") }
        }
        val faite = rechercheTmdb.faite
        val info = when {
            rechercheTmdb.enCours -> "Recherche…"
            faite.isNotEmpty() && rechercheTmdb.resultats.isEmpty() -> "Aucun résultat pour « $faite »."
            faite.isNotEmpty() && autres.isEmpty() -> "Rien d'autre sur TMDB pour « $faite »."
            else -> null
        }
        if (info != null) item(key = "info") { TexteInfo(info) }
        items(autres, key = { "tmdb-${it.tmdbId}" }) { r ->
            CarteResultat(r, suivies[r.tmdbId], ajoutsEnCours[r.tmdbId], onAjouter, onOuvrir, onApercu)
        }
    }
}

@Composable
private fun TitreBloc(texte: String) {
    Text(
        texte,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun TexteInfo(texte: String) {
    Text(texte, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// Un résultat TMDB : affiche, titre, année · pays · titre original, début du
// résumé ; puis « Ajouter » et « Plus tard » (« Ajout… » pendant l'envoi),
// ou « Dans ma liste » si la série est déjà suivie. Un appui sur la carte
// ouvre la fiche d'une série suivie, sinon son aperçu (décisions 74 et 77).
@Composable
private fun CarteResultat(
    r: ResultatTmdb,
    suivie: SerieSuivie?,
    ajoutEnCours: String?,
    onAjouter: (ResultatTmdb, Boolean) -> Unit,
    onOuvrir: (Long) -> Unit,
    onApercu: (Int) -> Unit,
) {
    val contenu: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 64 × 96 dp, comme les 64 × 96 pixels du site.
            AfficheSerie(r.affiche, 64.dp, 96.dp, "w154")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        r.titre,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (r.anime) Etiquette("Animé")
                }
                val details = detailsResultat(r)
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!r.resume.isNullOrBlank()) {
                    // Début du résumé seulement (2 lignes), comme sur le site.
                    Text(r.resume, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (suivie != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.coche),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(mentionDansListe(suivie.statut), style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        val libelleAjouter = "Ajouter ${r.titre} à ma liste"
                        Button(
                            onClick = { onAjouter(r, false) },
                            enabled = ajoutEnCours == null,
                            modifier = Modifier.semantics { contentDescription = libelleAjouter },
                        ) {
                            Text(
                                if (ajoutEnCours == "liste") "Ajout…" else "Ajouter",
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                        val libellePlusTard = "Mettre ${r.titre} dans Plus tard"
                        OutlinedButton(
                            onClick = { onAjouter(r, true) },
                            enabled = ajoutEnCours == null,
                            modifier = Modifier.semantics { contentDescription = libellePlusTard },
                        ) {
                            Text(
                                if (ajoutEnCours == "plus_tard") "Ajout…" else "Plus tard",
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
    }
    OutlinedCard(
        onClick = { if (suivie != null) onOuvrir(suivie.serieId) else onApercu(r.tmdbId) },
        modifier = Modifier.fillMaxWidth(),
    ) { contenu() }
}

// Haut de l'écran : le nom de l'appli et un menu (trois points).
@Composable
private fun EnTete(onActualiser: () -> Unit, onAPropos: () -> Unit, onDeconnexion: () -> Unit) {
    var menuOuvert by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // sous la barre d'état du téléphone
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Canapilou",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            // Le menu s'ouvre sous le bouton (Box : point d'ancrage du menu).
            Box {
                IconButton(onClick = { menuOuvert = true }) {
                    Icon(painterResource(R.drawable.menu), contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOuvert, onDismissRequest = { menuOuvert = false }) {
                    DropdownMenuItem(
                        text = { Text("Actualiser") },
                        onClick = { menuOuvert = false; onActualiser() },
                    )
                    DropdownMenuItem(
                        text = { Text("À propos") },
                        onClick = { menuOuvert = false; onAPropos() },
                    )
                    DropdownMenuItem(
                        text = { Text("Se déconnecter") },
                        onClick = { menuOuvert = false; onDeconnexion() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeEnSections(
    sections: Map<Section, List<SerieSuivie>>,
    titres: Map<CleSaison, Map<Int, String?>>,
    vusEnCours: Set<Long>,
    ouvertes: Map<Section, MutableState<Boolean>>,
    defilement: LazyListState,
    onVu: (SerieSuivie) -> Unit,
    onOuvrir: (Long) -> Unit,
) {
    LazyColumn(
        state = defilement,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (section in Section.entries) {
            val series = sections[section].orEmpty()
            if (series.isEmpty()) continue // section vide : pas affichée
            val ouverte = ouvertes.getValue(section)
            item(key = "section-${section.name}") {
                TitreSection(section.titre, series.size, ouverte.value) { ouverte.value = !ouverte.value }
            }
            if (ouverte.value) {
                items(series, key = { it.serieId }) { serie ->
                    CarteSerie(
                        s = serie,
                        titreProchain = titreProchain(serie, titres),
                        vuEnCours = serie.serieId in vusEnCours,
                        onVu = { onVu(serie) },
                        onOuvrir = { onOuvrir(serie.serieId) },
                    )
                }
            }
        }
    }
}

// Titre d'une section, « Terminé (3) », avec une flèche ; un appui la replie
// ou la déplie.
@Composable
private fun TitreSection(titre: String, nombre: Int, ouverte: Boolean, onBasculer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBasculer)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$titre ($nombre)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.chevron),
            contentDescription = if (ouverte) "Replier la section" else "Déplier la section",
            modifier = Modifier.rotate(if (ouverte) 180f else 0f),
        )
    }
}

// Une série de la liste : affiche, titre, progression, et bouton « Vu »
// dans la section « En cours ». Un appui sur la carte ouvre la fiche (le
// bouton « Vu » garde son propre appui).
@Composable
private fun CarteSerie(
    s: SerieSuivie,
    titreProchain: String?,
    vuEnCours: Boolean,
    onVu: () -> Unit,
    onOuvrir: () -> Unit,
    // Dans les résultats de recherche, il n'y a pas de sections : le statut
    // s'affiche alors à côté du titre.
    statutVisible: Boolean = false,
) {
    OutlinedCard(onClick = onOuvrir, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 46 × 69 dp, comme les 46 × 69 pixels du site ; taille TMDB w154
            // pour rester nette sur un téléphone (choix de Claude, à revoir).
            AfficheSerie(s.afficheChemin, 46.dp, 69.dp, "w154")
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        s.titre,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (s.estAnime) Etiquette("Animé")
                    if (statutVisible) Etiquette(libelleStatut(s.statut))
                }
                pourcentage(s)?.let { p -> BarreProgression(p) }
                Text(
                    ligneProgression(s, titreProchain),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (peutCocher(s)) BoutonVu(s, vuEnCours, onVu)
        }
    }
}

// Bouton « Vu » : coche dessinée + « Vu » ; « … » pendant l'envoi. Les
// lecteurs d'écran annoncent « Marquer S1·E6 de … comme vu », comme sur le site.
@Composable
private fun BoutonVu(s: SerieSuivie, enCours: Boolean, onVu: () -> Unit) {
    val saison = s.prochaineSaison ?: return
    val episode = s.prochainEpisode ?: return
    val description = "Marquer ${libelleEpisode(saison, episode)} de ${s.titre} comme vu"
    Button(
        onClick = onVu,
        enabled = !enCours,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        if (enCours) {
            Text("…", modifier = Modifier.clearAndSetSemantics {})
        } else {
            Icon(painterResource(R.drawable.coche), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Vu", modifier = Modifier.clearAndSetSemantics {})
        }
    }
}
