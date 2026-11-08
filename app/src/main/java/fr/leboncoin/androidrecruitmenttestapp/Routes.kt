package fr.leboncoin.androidrecruitmenttestapp

/**
 * All destinations of the single-activity app.
 *
 * The detail route carries only the album id: the content is re-observed
 * from the Room cache by [DetailsViewModel] (see ARCHITECTURE.md §4) — the
 * same id-based pattern the previous Intent-extra navigation used, so the
 * ViewModels did not change.
 */
object Routes {

    const val ALBUMS = "albums"
    const val FAVORITES = "favorites"

    /** Name of the id route argument; also the nav-args key in SavedStateHandle. */
    const val ALBUM_ID_ARG = "albumId"

    /**
     * Source tab of the detail navigation; drives the shared-element key so
     * the detail image pairs with the thumbnail of the tab it was opened
     * from (the other list must not match it during tab transitions).
     */
    const val SRC_ARG = "src"

    /** Pattern of the detail destination ("album/42?src=albums"). */
    const val ALBUM_DETAIL = "album/{$ALBUM_ID_ARG}?$SRC_ARG={$SRC_ARG}"

    /** The two destinations owning the bottom bar. */
    val topLevel = setOf(ALBUMS, FAVORITES)

    fun albumDetail(albumId: Int, source: String) = "album/$albumId?$SRC_ARG=$source"
}
