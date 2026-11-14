-- =====================================================================
-- Suivi de séries — séparer séries et animés
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les deux fichiers précédents.
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. Classement automatique, dans le catalogue partagé.
--    Rempli par la fonction serveur « tmdb » à chaque ajout ou mise à jour
--    d'une fiche : vrai si TMDB donne le genre Animation ET le japonais comme
--    langue d'origine (décision 24). Vide (NULL) = pas encore déterminé
--    (séries ajoutées avant ce changement) : le site les fera mettre à jour.
-- ---------------------------------------------------------------------
alter table public.series
  add column est_anime boolean;


-- ---------------------------------------------------------------------
-- 2. Correction à la main, propre à chaque utilisateur (dans son suivi) :
--    'serie' ou 'anime' ; vide (NULL) = on suit le classement automatique.
--    Les droits et règles RLS existants de « suivis » s'appliquent déjà
--    à cette nouvelle colonne.
-- ---------------------------------------------------------------------
alter table public.suivis
  add column categorie_manuelle text
    check (categorie_manuelle in ('serie', 'anime'));


-- ---------------------------------------------------------------------
-- 3. La vue « ma_liste » gagne trois colonnes, ajoutées À LA FIN
--    (PostgreSQL n'accepte de remplacer une vue que si les colonnes
--    existantes restent identiques et dans le même ordre) :
--      categorie_manuelle : la correction de l'utilisateur (ou vide) ;
--      anime_detecte      : le classement automatique (ou vide) ;
--      est_anime          : le classement final (correction, sinon automatique,
--                           sinon « série »).
--    Le reste est inchangé (voir 20261113193500_vue_ma_liste.sql).
-- ---------------------------------------------------------------------
create or replace view public.ma_liste
with (security_invoker = true)
as
select
  su.serie_id,
  se.tmdb_id,
  se.titre,
  se.affiche_chemin,
  se.statut_diffusion,
  su.statut_manuel,
  coalesce(
    su.statut_manuel,
    case
      when calc.nb_vus = 0               then 'a_voir'
      when calc.nb_vus < calc.nb_diffuses then 'en_cours'
      when se.statut_diffusion in ('Ended', 'Canceled') then 'termine'
      else 'a_jour'
    end
  ) as statut,
  calc.nb_diffuses,
  calc.nb_vus,
  calc.prochaine_saison,
  calc.prochain_episode,
  greatest(
    su.ajoute_le,
    (select max(v.vu_le)
       from public.episodes_vus v
      where v.utilisateur_id = su.utilisateur_id
        and v.serie_id = su.serie_id)
  ) as derniere_activite,
  -- Nouvelles colonnes :
  su.categorie_manuelle,
  se.est_anime as anime_detecte,
  coalesce(
    case su.categorie_manuelle when 'anime' then true when 'serie' then false end,
    se.est_anime,
    false
  ) as est_anime
from public.suivis su
join public.series se on se.id = su.serie_id
cross join lateral (
  select
    count(*)::integer                           as nb_diffuses,
    (count(*) filter (where d.vu))::integer     as nb_vus,
    (array_agg(d.saison  order by d.saison, d.episode) filter (where not d.vu))[1] as prochaine_saison,
    (array_agg(d.episode order by d.saison, d.episode) filter (where not d.vu))[1] as prochain_episode
  from (
    select
      sa.numero as saison,
      e.episode,
      exists (
        select 1
          from public.episodes_vus v
         where v.utilisateur_id = su.utilisateur_id
           and v.serie_id = su.serie_id
           and v.saison = sa.numero
           and v.episode = e.episode
      ) as vu
    from public.saisons sa
    cross join lateral generate_series(1, sa.nb_episodes) as e(episode)
    where sa.serie_id = su.serie_id
      and sa.numero >= 1
      and (
        se.statut_diffusion in ('Ended', 'Canceled')
        or sa.numero < se.derniere_saison_diffusee
        or (sa.numero = se.derniere_saison_diffusee
            and e.episode <= se.dernier_episode_diffuse)
      )
  ) d
) calc;

-- Les droits déjà donnés sur la vue (lecture pour « authenticated », rien
-- pour « anon ») sont conservés par « create or replace ».
