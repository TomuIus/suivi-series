-- =====================================================================
-- Suivi de séries — schéma initial de la base de données
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor).
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Table « series » : une ligne par série connue du site.
--
-- Partagée entre tous les utilisateurs. Contient les identifiants et
-- la petite fiche copiée depuis TMDB. Seule la fonction TMDB (côté
-- serveur) pourra y écrire ; les utilisateurs ne font que la lire.
-- ---------------------------------------------------------------------
create table public.series (
  id                        bigint generated always as identity primary key,
  tmdb_id                   integer not null unique,  -- identifiant chez TMDB
  imdb_id                   text,                     -- identifiant IMDb (ex. « tt0944947 »)
  tvdb_id                   integer,                  -- identifiant TheTVDB
  titre                     text not null,            -- titre en français si TMDB en a un
  titre_original            text,
  affiche_chemin            text,                     -- chemin de l'affiche chez TMDB (ex. « /abc.jpg »)
  statut_diffusion          text,                     -- statut TMDB : « Returning Series », « Ended »...
  derniere_saison_diffusee  integer,                  -- dernier épisode déjà sorti :
  dernier_episode_diffuse   integer,                  --   saison et numéro
  fiche_maj_le              timestamptz not null default now(),  -- dernière mise à jour depuis TMDB
  ajoutee_le                timestamptz not null default now()
);


-- ---------------------------------------------------------------------
-- 2. Table « saisons » : une ligne par saison d'une série.
--
-- Donne le nombre d'épisodes de chaque saison. La saison 0 contient
-- les épisodes spéciaux. Supprimer une série supprime ses saisons.
-- ---------------------------------------------------------------------
create table public.saisons (
  serie_id     bigint  not null references public.series (id) on delete cascade,
  numero       integer not null check (numero >= 0),
  nb_episodes  integer not null check (nb_episodes >= 0),
  primary key (serie_id, numero)
);


-- ---------------------------------------------------------------------
-- 3. Table « suivis » : une ligne par couple utilisateur–série.
--
-- « statut_manuel » est vide (NULL) tant que le statut est calculé
-- automatiquement ; il contient un statut quand l'utilisateur l'a
-- choisi lui-même (« abandonne » est toujours manuel).
-- Supprimer un compte supprime ses suivis.
-- ---------------------------------------------------------------------
create table public.suivis (
  utilisateur_id  uuid   not null default auth.uid()
                         references auth.users (id) on delete cascade,
  serie_id        bigint not null references public.series (id),
  statut_manuel   text   check (statut_manuel in
                    ('a_voir', 'en_cours', 'a_jour', 'termine', 'abandonne')),
  ajoute_le       timestamptz not null default now(),
  primary key (utilisateur_id, serie_id)
);


-- ---------------------------------------------------------------------
-- 4. Table « episodes_vus » : une ligne par épisode coché.
--
-- On ne peut cocher que les épisodes d'une série que l'on suit
-- (lien vers « suivis »). Retirer une série de sa liste efface
-- aussi ses épisodes cochés (« on delete cascade »).
-- ---------------------------------------------------------------------
create table public.episodes_vus (
  utilisateur_id  uuid    not null default auth.uid(),
  serie_id        bigint  not null,
  saison          integer not null check (saison >= 0),
  episode         integer not null check (episode >= 0),
  vu_le           timestamptz not null default now(),
  primary key (utilisateur_id, serie_id, saison, episode),
  foreign key (utilisateur_id, serie_id)
    references public.suivis (utilisateur_id, serie_id) on delete cascade
);


-- ---------------------------------------------------------------------
-- 5. Accès aux tables depuis l'API (« grants »).
--
-- Depuis 2026, Supabase n'ouvre plus automatiquement les nouvelles
-- tables à son API : il faut accorder chaque droit explicitement.
-- On ne donne rien au rôle « anon » (visiteur non connecté).
-- ---------------------------------------------------------------------

-- Utilisateurs connectés : lecture seule sur les séries et saisons...
grant select on public.series, public.saisons to authenticated;
-- ... et gestion de leurs propres suivis et épisodes vus
-- (les règles de la partie 6 limitent chacun à ses propres lignes).
grant select, insert, update, delete on public.suivis to authenticated;
grant select, insert, delete on public.episodes_vus to authenticated;

-- Fonction TMDB (côté serveur, rôle « service_role ») : écriture des
-- séries et saisons. Ce rôle ne doit jamais être utilisé dans le site.
grant select, insert, update, delete on public.series, public.saisons to service_role;
grant usage, select on all sequences in schema public to service_role;


-- ---------------------------------------------------------------------
-- 6. Sécurité ligne par ligne (« Row Level Security »).
--
-- Une fois activée, une table refuse tout accès sauf ce qu'autorisent
-- les règles (« policies ») ci-dessous. « (select auth.uid()) » est
-- l'identifiant de l'utilisateur connecté ; l'écrire entre
-- parenthèses avec « select » le fait calculer une seule fois par
-- requête au lieu d'une fois par ligne (recommandation de Supabase).
-- ---------------------------------------------------------------------
alter table public.series       enable row level security;
alter table public.saisons      enable row level security;
alter table public.suivis       enable row level security;
alter table public.episodes_vus enable row level security;

-- Séries et saisons : lisibles par tout utilisateur connecté.
create policy "Lecture des séries par les utilisateurs connectés"
  on public.series for select to authenticated
  using (true);

create policy "Lecture des saisons par les utilisateurs connectés"
  on public.saisons for select to authenticated
  using (true);

-- Suivis : chacun ne voit et ne modifie que les siens.
create policy "Voir ses propres suivis"
  on public.suivis for select to authenticated
  using ((select auth.uid()) = utilisateur_id);

create policy "Ajouter ses propres suivis"
  on public.suivis for insert to authenticated
  with check ((select auth.uid()) = utilisateur_id);

create policy "Modifier ses propres suivis"
  on public.suivis for update to authenticated
  using ((select auth.uid()) = utilisateur_id)
  with check ((select auth.uid()) = utilisateur_id);

create policy "Supprimer ses propres suivis"
  on public.suivis for delete to authenticated
  using ((select auth.uid()) = utilisateur_id);

-- Épisodes vus : idem (pas de modification, on coche ou on décoche).
create policy "Voir ses propres épisodes vus"
  on public.episodes_vus for select to authenticated
  using ((select auth.uid()) = utilisateur_id);

create policy "Cocher ses propres épisodes"
  on public.episodes_vus for insert to authenticated
  with check ((select auth.uid()) = utilisateur_id);

create policy "Décocher ses propres épisodes"
  on public.episodes_vus for delete to authenticated
  using ((select auth.uid()) = utilisateur_id);
