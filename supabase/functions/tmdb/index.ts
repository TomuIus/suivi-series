// SPDX-License-Identifier: AGPL-3.0-or-later
// Projet suivi-series — fonction serveur « tmdb ».
//
// Rôle : faire l'intermédiaire entre le site et l'API de TMDB.
//
// Pourquoi passer par ici plutôt que d'appeler TMDB depuis le navigateur ?
//   1. Le jeton TMDB reste secret : il ne quitte jamais les serveurs Supabase.
//   2. Seule cette fonction écrit dans le catalogue partagé (tables `series`
//      et `saisons`) ; les utilisateurs n'ont qu'un droit de lecture dessus.
//
// Le site l'appelle en POST avec un corps JSON, sous l'une de ces deux formes :
//   { "action": "rechercher", "texte": "game of thrones" }
//       → { "resultats": [ { tmdbId, titre, titreOriginal, annee, affiche, resume, pays } ] }
//   { "action": "ajouter", "tmdbId": 1399 }
//       → { "id": <identifiant interne de la série>, "titre": "..." }
// En cas de problème : un code HTTP d'erreur et { "erreur": "message en français" }.

import { createClient, type SupabaseClient } from 'npm:@supabase/supabase-js@2.119.0'
// En-têtes CORS officiels, tenus à jour par Supabase (disponibles depuis la 2.95.0).
import { corsHeaders } from 'npm:@supabase/supabase-js@2.119.0/cors'

// ---------------------------------------------------------------------------
// Réglages
// ---------------------------------------------------------------------------

// SUPABASE_URL et SUPABASE_SECRET_KEYS sont fournis automatiquement par Supabase.
// TMDB_TOKEN est le « jeton d'accès en lecture » (Read Access Token) de TMDB :
// c'est vous qui l'ajoutez dans le tableau de bord (Edge Functions → Secrets).
const SUPABASE_URL = Deno.env.get('SUPABASE_URL') ?? ''
const CLE_SECRETE = lireCleSecrete()
const TMDB_TOKEN = Deno.env.get('TMDB_TOKEN') ?? ''

const TMDB_API = 'https://api.themoviedb.org/3'
const LANGUE = 'fr-FR'
const LONGUEUR_MAX_RECHERCHE = 100 // caractères
const DELAI_MAX_TMDB = 10_000 // millisecondes — à revoir après des tests
const ENTIER_MAX_POSTGRES = 2_147_483_647 // la colonne series.tmdb_id est un « int »

// SUPABASE_SECRET_KEYS contient un objet JSON du type { "default": "sb_secret_..." }.
function lireCleSecrete(): string {
  try {
    const cles = JSON.parse(Deno.env.get('SUPABASE_SECRET_KEYS') ?? '{}')
    return typeof cles.default === 'string' ? cles.default : ''
  } catch {
    return ''
  }
}

// ---------------------------------------------------------------------------
// Outils
// ---------------------------------------------------------------------------

// Une erreur « prévue » : on connaît le code HTTP et le message à renvoyer.
class ErreurHttp extends Error {
  statut: number
  constructor(statut: number, message: string) {
    super(message)
    this.statut = statut
  }
}

function repondre(statut: number, corps: unknown): Response {
  return new Response(JSON.stringify(corps), {
    status: statut,
    headers: { ...corsHeaders, 'Content-Type': 'application/json; charset=utf-8' },
  })
}

// Client « administrateur » : il utilise la clé secrète, donc il passe outre
// les règles RLS. Il sert uniquement à vérifier le jeton de l'utilisateur et
// à écrire dans le catalogue. Créé une seule fois, au premier appel.
let clientAdminEnCache: SupabaseClient | null = null

function clientAdmin(): SupabaseClient {
  if (!SUPABASE_URL || !CLE_SECRETE || !TMDB_TOKEN) {
    // On indique dans les journaux CE QUI manque, jamais les valeurs.
    console.error('Configuration incomplète :', {
      SUPABASE_URL: Boolean(SUPABASE_URL),
      SUPABASE_SECRET_KEYS: Boolean(CLE_SECRETE),
      TMDB_TOKEN: Boolean(TMDB_TOKEN),
    })
    throw new ErreurHttp(500, 'Le serveur est mal configuré.')
  }
  clientAdminEnCache ??= createClient(SUPABASE_URL, CLE_SECRETE, {
    // Côté serveur, aucune session à mémoriser ni à rafraîchir.
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
  })
  return clientAdminEnCache
}

// ---------------------------------------------------------------------------
// Étape 1 : qui appelle ?
// ---------------------------------------------------------------------------

// Supabase a déjà rejeté les appels sans jeton valide (verify_jwt = true dans
// config.toml). On revérifie ici, car un jeton « valide » peut aussi être
// celui d'un visiteur non connecté (rôle « anon ») : on exige un vrai compte.
async function verifierUtilisateur(admin: SupabaseClient, req: Request): Promise<string> {
  const entete = req.headers.get('Authorization') ?? ''
  const jeton = /^Bearer\s+(.+)$/i.exec(entete)?.[1] ?? ''
  if (!jeton) throw new ErreurHttp(401, 'Connexion requise.')

  // getClaims vérifie la signature et la date d'expiration du jeton.
  const { data, error } = await admin.auth.getClaims(jeton)
  const infos = data?.claims
  if (error || !infos) throw new ErreurHttp(401, 'Session invalide ou expirée, reconnectez-vous.')

  if (infos.role !== 'authenticated' || !infos.sub || infos.is_anonymous === true) {
    throw new ErreurHttp(403, 'Connexion requise.')
  }
  return infos.sub
}

// ---------------------------------------------------------------------------
// Étape 2 : que demande-t-on ? (on ne fait jamais confiance au contenu reçu)
// ---------------------------------------------------------------------------

type Demande =
  | { action: 'rechercher'; texte: string }
  | { action: 'ajouter'; tmdbId: number }

async function lireDemande(req: Request): Promise<Demande> {
  let corps: unknown
  try {
    corps = await req.json()
  } catch {
    throw new ErreurHttp(400, 'Requête illisible (JSON attendu).')
  }
  if (typeof corps !== 'object' || corps === null) {
    throw new ErreurHttp(400, 'Requête invalide.')
  }
  const { action, texte, tmdbId } = corps as Record<string, unknown>

  if (action === 'rechercher') {
    const nettoye = typeof texte === 'string' ? texte.trim() : ''
    if (nettoye.length === 0 || nettoye.length > LONGUEUR_MAX_RECHERCHE) {
      throw new ErreurHttp(400, `La recherche doit faire entre 1 et ${LONGUEUR_MAX_RECHERCHE} caractères.`)
    }
    return { action, texte: nettoye }
  }

  if (action === 'ajouter') {
    if (typeof tmdbId !== 'number' || !Number.isInteger(tmdbId) || tmdbId <= 0 || tmdbId > ENTIER_MAX_POSTGRES) {
      throw new ErreurHttp(400, 'Identifiant TMDB invalide.')
    }
    return { action, tmdbId }
  }

  throw new ErreurHttp(400, 'Action inconnue.')
}

// ---------------------------------------------------------------------------
// Étape 3 : parler à TMDB
// ---------------------------------------------------------------------------

async function appelerTmdb(chemin: string, parametres: Record<string, string>): Promise<unknown> {
  const url = new URL(TMDB_API + chemin)
  url.searchParams.set('language', LANGUE)
  for (const [cle, valeur] of Object.entries(parametres)) url.searchParams.set(cle, valeur)

  let reponse: Response
  try {
    reponse = await fetch(url, {
      headers: { Authorization: `Bearer ${TMDB_TOKEN}`, Accept: 'application/json' },
      signal: AbortSignal.timeout(DELAI_MAX_TMDB),
    })
  } catch (erreur) {
    console.error('TMDB injoignable :', erreur)
    throw new ErreurHttp(502, 'TMDB ne répond pas, réessayez plus tard.')
  }

  if (reponse.ok) return await reponse.json()

  // Le corps d'erreur de TMDB ne contient pas notre jeton : on peut le journaliser.
  const detail = await reponse.text().catch(() => '')
  if (reponse.status === 404) throw new ErreurHttp(404, 'Série introuvable sur TMDB.')
  if (reponse.status === 429) throw new ErreurHttp(503, 'TMDB est saturé, réessayez dans un instant.')
  if (reponse.status === 401) {
    console.error('TMDB refuse le jeton : vérifiez le secret TMDB_TOKEN.', detail)
  } else {
    console.error('Réponse TMDB inattendue :', reponse.status, detail)
  }
  throw new ErreurHttp(502, 'Erreur côté TMDB, réessayez plus tard.')
}

// Les seuls champs TMDB qui nous intéressent (TMDB en renvoie beaucoup d'autres).
interface ResultatRechercheTmdb {
  id: number
  name?: string
  original_name?: string
  first_air_date?: string
  poster_path?: string | null
  overview?: string
  origin_country?: string[]
}

interface FicheTmdb {
  id: number
  name?: string
  original_name?: string
  poster_path?: string | null
  status?: string
  last_episode_to_air?: { season_number?: number; episode_number?: number } | null
  seasons?: { season_number?: number; episode_count?: number }[]
  external_ids?: { imdb_id?: string | null; tvdb_id?: number | null }
}

// Action « rechercher » : on renvoie une liste allégée, rien n'est enregistré.
async function rechercher(texte: string) {
  const donnees = (await appelerTmdb('/search/tv', {
    query: texte,
    include_adult: 'false',
    page: '1', // 20 résultats maximum
  })) as { results?: ResultatRechercheTmdb[] }

  return (donnees.results ?? []).map((r) => ({
    tmdbId: r.id,
    titre: r.name || r.original_name || '(sans titre)',
    titreOriginal: r.original_name || null,
    annee: Number.parseInt(r.first_air_date ?? '', 10) || null, // « 2011-04-17 » → 2011
    affiche: r.poster_path || null, // chemin seul ; le site construit l'adresse de l'image
    resume: r.overview || null,
    pays: r.origin_country ?? [], // ex. ["JP"] : utile plus tard pour repérer les animés
  }))
}

// Action « ajouter » : copie (ou met à jour) la fiche TMDB dans notre catalogue.
// Le site crée ensuite lui-même le « suivi » de l'utilisateur avec l'id renvoyé.
async function ajouter(admin: SupabaseClient, tmdbId: number) {
  // Une seule requête TMDB : la fiche + ses identifiants externes (IMDb, TVDB).
  const fiche = (await appelerTmdb(`/tv/${tmdbId}`, {
    append_to_response: 'external_ids',
  })) as FicheTmdb

  // « upsert » = insérer, ou mettre à jour si ce tmdb_id existe déjà.
  // ajoutee_le n'est pas envoyé : il garde sa date de première insertion.
  const { data: serie, error: erreurSerie } = await admin
    .from('series')
    .upsert(
      {
        tmdb_id: fiche.id,
        imdb_id: fiche.external_ids?.imdb_id || null,
        tvdb_id: fiche.external_ids?.tvdb_id || null,
        titre: fiche.name || fiche.original_name || '(sans titre)',
        titre_original: fiche.original_name || null,
        affiche_chemin: fiche.poster_path || null,
        statut_diffusion: fiche.status || null,
        derniere_saison_diffusee: fiche.last_episode_to_air?.season_number ?? null,
        dernier_episode_diffuse: fiche.last_episode_to_air?.episode_number ?? null,
        fiche_maj_le: new Date().toISOString(),
      },
      { onConflict: 'tmdb_id' },
    )
    .select('id, titre')
    .single()

  if (erreurSerie || !serie) {
    console.error('Échec de l\'enregistrement de la série :', erreurSerie)
    throw new ErreurHttp(500, 'Impossible d\'enregistrer la série.')
  }

  // Saisons (la saison 0 correspond aux épisodes spéciaux chez TMDB).
  // La Map évite les doublons : un même numéro deux fois ferait échouer l'upsert.
  const parNumero = new Map<number, { serie_id: number; numero: number; nb_episodes: number }>()
  for (const s of fiche.seasons ?? []) {
    const numero = s.season_number
    if (typeof numero !== 'number' || !Number.isInteger(numero) || numero < 0) continue
    parNumero.set(numero, {
      serie_id: serie.id,
      numero,
      // Pour une saison en cours, TMDB peut compter des épisodes pas encore
      // diffusés : à revoir après des tests.
      nb_episodes: Math.max(0, s.episode_count ?? 0),
    })
  }
  const saisons = [...parNumero.values()]

  if (saisons.length > 0) {
    const { error } = await admin.from('saisons').upsert(saisons, { onConflict: 'serie_id,numero' })
    if (error) {
      console.error('Échec de l\'enregistrement des saisons :', error)
      throw new ErreurHttp(500, 'Impossible d\'enregistrer les saisons.')
    }
  }

  // Si TMDB a réorganisé les saisons (fréquent pour les animés), on retire
  // celles qu'il ne liste plus. Les épisodes vus ne sont pas touchés.
  let suppression = admin.from('saisons').delete().eq('serie_id', serie.id)
  if (saisons.length > 0) {
    suppression = suppression.not('numero', 'in', `(${saisons.map((s) => s.numero).join(',')})`)
  }
  const { error: erreurSuppression } = await suppression
  if (erreurSuppression) {
    // Pas bloquant : la fiche est enregistrée, seul le ménage a échoué.
    console.error('Échec du retrait des anciennes saisons :', erreurSuppression)
  }

  return { id: serie.id, titre: serie.titre }
}

// ---------------------------------------------------------------------------
// Point d'entrée : chaque appel HTTP passe ici
// ---------------------------------------------------------------------------

Deno.serve(async (req) => {
  // Le navigateur envoie d'abord une requête OPTIONS (« preflight ») pour
  // demander si le site tomuius.github.io a le droit d'appeler ce serveur.
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    if (req.method !== 'POST') throw new ErreurHttp(405, 'Méthode non autorisée.')

    const admin = clientAdmin()
    await verifierUtilisateur(admin, req)
    const demande = await lireDemande(req)

    if (demande.action === 'rechercher') {
      return repondre(200, { resultats: await rechercher(demande.texte) })
    }
    return repondre(200, await ajouter(admin, demande.tmdbId))
  } catch (erreur) {
    if (erreur instanceof ErreurHttp) return repondre(erreur.statut, { erreur: erreur.message })
    console.error('Erreur inattendue :', erreur)
    return repondre(500, { erreur: 'Erreur interne du serveur.' })
  }
})
