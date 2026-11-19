-- =====================================================================
-- Canapilou — mise à jour régulière des fiches TMDB (décision 42)
--
-- À exécuter UNE SEULE FOIS dans l'éditeur SQL de Supabase
-- (tableau de bord > SQL Editor), après les fichiers précédents et
-- AVANT d'envoyer la v25 du site sur GitHub : la v25 lit la nouvelle
-- colonne « fiche_a_rafraichir » ; sans elle, « Ma liste » ne se
-- chargerait plus.
--
-- Pourquoi : les conditions de TMDB interdisent de garder ses
-- informations plus de 6 mois. Jusqu'ici, une fiche n'était remise à
-- jour qu'à l'ouverture de la série. Désormais :
--   - le site fait remettre à jour, à l'ouverture de « Ma liste », les
--     séries de la liste dont la fiche a plus de 5 mois (point 1) ;
--   - les séries que plus personne ne suit et dont la fiche a plus de
--     5 mois sont supprimées du catalogue (point 2).
-- 5 mois : marge d'un mois avant la limite (valeur choisie par Claude,
-- à revoir après des tests). Elle figure aux deux endroits ci-dessous.
--
-- Licence : AGPL-3.0 (voir le fichier LICENSE à la racine du dépôt).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. La vue « ma_liste » : NOUVELLE colonne « fiche_a_rafraichir »,
--    ajoutée à la fin : vrai si la fiche TMDB de la série a plus de
--    5 mois. Le reste est identique à 20261114200000_speciaux_statistiques.sql.
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
  spec.nb_vus as nb_speciaux_vus,
  -- Nouvelle colonne (ajoutée à la fin, comme PostgreSQL l'exige) :
  (se.fiche_maj_le < now() - interval '5 months') as fiche_a_rafraichir
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
-- 2. Fonction « nettoyer_catalogue » : supprime du catalogue les séries
--    que plus personne ne suit ET dont la fiche a plus de 5 mois (leurs
--    saisons partent avec elles : « on delete cascade »). Renvoie le
--    nombre de séries supprimées. Le site l'appelle une fois par visite.
--
--    Pourquoi pas dès qu'une série n'est plus suivie : l'import
--    « Remplacer toute ma liste » (décision 40) vide la liste puis suit à
--    nouveau des séries déjà dans le catalogue ; une suppression immédiate
--    les ferait disparaître entre les deux. Avec la condition des 5 mois,
--    une série retirée par erreur puis ajoutée de nouveau est aussi
--    retrouvée telle quelle.
--
--    « security definer » : la fonction s'exécute avec les droits de son
--    propriétaire, pour voir les suivis de TOUS les utilisateurs (une
--    série suivie par quelqu'un d'autre ne doit pas être supprimée) et
--    pour écrire dans « series », fermé en écriture aux utilisateurs. Elle
--    ne prend aucun paramètre et ne touche qu'aux séries orphelines et
--    anciennes : sans risque pour les données de quiconque.
--    « search_path » vide : les noms de tables sont écrits en entier
--    (« public.series »), précaution recommandée pour ce genre de fonction.
-- ---------------------------------------------------------------------
create or replace function public.nettoyer_catalogue()
returns integer
language plpgsql
security definer
set search_path = ''
as $$
declare
  nb integer;
begin
  delete from public.series se
   where se.fiche_maj_le < now() - interval '5 months'
     and not exists (
       select 1 from public.suivis su where su.serie_id = se.id
     );
  get diagnostics nb = row_count;
  return nb;
end;
$$;

-- Droit d'appel : utilisateurs connectés seulement (pas les visiteurs).
revoke execute on function public.nettoyer_catalogue() from public, anon;
grant execute on function public.nettoyer_catalogue() to authenticated;
