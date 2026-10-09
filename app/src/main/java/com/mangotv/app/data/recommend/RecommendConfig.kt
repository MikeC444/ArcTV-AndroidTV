package com.mangotv.app.data.recommend

/**
 * "Picked for you" -- every number that shapes the recommendation lives here, named, so tuning never means hunting
 * through logic. Ported from the web app's `domain/recommend/config.ts`, value for value, so both apps rank the same way.
 * The score is an internal ranking value only. It is not the IMDb rating, a star rating, or a probability that someone
 * will enjoy a film.
 */
object RecommendConfig {
    /** What one movie says about a profile's taste. The strongest applicable signal wins; explicit feedback always takes precedence. */
    const val WEIGHT_LIKE = 5.0
    const val WEIGHT_DISLIKE = -5.0
    /** Finished the movie (the app's own completion rule), with no explicit feedback. */
    const val WEIGHT_COMPLETED = 2.0
    /** On the watchlist, with no explicit feedback and not finished. */
    const val WEIGHT_WATCHLIST = 1.0

    /** How the three similarities combine. A category with no usable data is left out and the rest are renormalised. */
    const val CATEGORY_WEIGHT_GENRE = 0.6
    const val CATEGORY_WEIGHT_DIRECTOR = 0.25
    const val CATEGORY_WEIGHT_CAST = 0.15

    const val MAX_RESULTS = 20

    /**
     * Composition step (Rotation.kt): the row follows the profile's own taste split across genres and a refresh swaps most of it. The
     * best ROTATION_ANCHORS picks always stay; the other places go to genres in proportion to the profile's taste, each drawn from that
     * genre's other genuinely scored candidates that score at least ROTATION_FLOOR of that genre's best. A candidate shown in the
     * previous row counts ROTATION_REPEAT_WEIGHT times as much in the draw, so most of the row is different next time.
     */
    const val ROTATION_ANCHORS = 5
    const val ROTATION_FLOOR = 0.6
    const val ROTATION_REPEAT_WEIGHT = 0.15

    /** Shortlist: so every genre the profile likes has candidates to score, each gets this many (at least) out of a budget shared by taste share. */
    const val SHORTLIST_GENRE_BUDGET = 36
    const val SHORTLIST_GENRE_MIN = 4

    /** Diversity step (applied after scoring, separate from it): one movie of the profile's can be the stated reason for at most this many picks. */
    const val MAX_PICKS_PER_SOURCE = 3
    /** Shortlist: this many by overall genre match, the rest taken round-robin from the best matches of each of the profile's own movies. */
    const val SHORTLIST_BY_SCORE = 20
    /** How many of the profile's own movies take a turn in the round-robin (strongest signals first). */
    const val SHORTLIST_SOURCE_MOVIES = 12

    /** Fewer than this many interactions (with at least one positive) and the row is the labelled popular-movies fallback instead. */
    const val MIN_INTERACTIONS_FOR_PERSONALISATION = 3

    /** Billing-order cast beyond this adds noise, not taste. */
    const val CAST_FEATURE_LIMIT = 12

    /** Network budget: how many candidates get a detail lookup per refresh, how many of the profile's own movies are looked up, and how many run at once. */
    const val CANDIDATE_DETAIL_FETCH_LIMIT = 60
    const val INTERACTION_DETAIL_FETCH_LIMIT = 60
    const val DETAIL_FETCH_CONCURRENCY = 4

    /** Persistent cache of movie features (genres / directors / cast) so a refresh rarely touches the network. */
    const val FEATURE_CACHE_MAX_ENTRIES = 800
    const val FEATURE_CACHE_TTL_MS = 30L * 24 * 3600_000

    /** How long a title removed from "Picked for you" stays out of the row; after that the algorithm decides again whether it is still a good pick. */
    const val PICKED_DISMISS_DAYS = 5
    const val PICKED_ROW_ID = "picked_for_you"
    const val PICKED_ROW_TITLE = "Picked for you · ArcTV Plus"
    const val POPULAR_ROW_TITLE = "Popular movies, not personalised yet · ArcTV Plus"
}
