-- =====================================================================
-- Suivi de séries — épisodes spéciaux cochés comptés dans les
-- statistiques (décision 32)
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les fichiers précédents
-- et APRÈS avoir redéployé la fonction « tmdb » (voir point 2).
--
-- Rien ne change pour les statuts ni pour la barre de progression :
-- nb_diffuses, nb_vus et le statut automatique ignorent toujours les
-- épisodes spéciaux (saison 0), cochés ou non.
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. La vue « ma_liste » :
--      minutes_vues      : compte maintenant AUSSI les épisodes spéciaux
--                          cochés (× durée moyenne de la saison 0) ;
--      nb_vus_sans_duree : idem pour les spéciaux cochés sans durée connue ;
--      nb_speciaux_vus   : NOUVELLE colonne, ajoutée à la fin : nombre
--                          d'épisodes spéciaux cochés.
--    Le reste est identique à 20261114190000_durees.sql.
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
  su.categorie_manuelle,
  se.est_anime as anime_detecte,
  coalesce(
    case su.categorie_manuelle when 'anime' then true when 'serie' then false end,
    se.est_anime,
    false
  ) as est_anime,
  calc.minutes_vues + spec.minutes          as minutes_vues,       -- spéciaux cochés compris
  calc.nb_vus_sans_duree + spec.sans_duree  as nb_vus_sans_duree,  -- spéciaux cochés compris
  (se.durees_maj_le is null) as durees_a_calculer,
  -- Nouvelle colonne :
  spec.nb_vus as nb_speciaux_vus
from public.suivis su
join public.series se on se.id = su.serie_id
cross join lateral (
  select
    count(*)::integer                           as nb_diffuses,
    (count(*) filter (where d.vu))::integer     as nb_vus,
    (array_agg(d.saison  order by d.saison, d.episode) filter (where not d.vu))[1] as prochaine_saison,
    (array_agg(d.episode order by d.saison, d.episode) filter (where not d.vu))[1] as prochain_episode,
    coalesce(sum(d.duree) filter (where d.vu), 0)::integer                 as minutes_vues,
    (count(*) filter (where d.vu and d.duree is null))::integer            as nb_vus_sans_duree
  from (
    select
      sa.numero as saison,
      e.episode,
      sa.duree_moyenne as duree,
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
) calc
-- Épisodes spéciaux (saison 0) cochés : tous ceux que l'utilisateur a
-- cochés comptent (pas de notion « déjà sorti » pour les spéciaux).
cross join lateral (
  select
    count(*)::integer                                          as nb_vus,
    coalesce(sum(s0.duree_moyenne), 0)::integer                as minutes,
    (count(*) filter (where s0.duree_moyenne is null))::integer as sans_duree
  from public.episodes_vus v
  left join public.saisons s0
    on s0.serie_id = v.serie_id
   and s0.numero = 0
  where v.utilisateur_id = su.utilisateur_id
    and v.serie_id = su.serie_id
    and v.saison = 0
) spec;

-- Les droits déjà donnés sur la vue (lecture pour « authenticated », rien
-- pour « anon ») et l'option security_invoker sont conservés.


-- ---------------------------------------------------------------------
-- 2. Faire recalculer les durées de toutes les séries : jusqu'ici la
--    fonction « tmdb » ne calculait pas la durée des spéciaux. Une fois la
--    NOUVELLE fonction déployée, le site remettra à jour, en arrière-plan,
--    les séries de votre liste à l'ouverture de « Ma liste » (comme à la v13).
-- ---------------------------------------------------------------------
update public.series
   set durees_maj_le = null;
