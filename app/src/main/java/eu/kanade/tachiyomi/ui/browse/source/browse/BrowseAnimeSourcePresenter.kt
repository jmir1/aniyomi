package eu.kanade.tachiyomi.ui.browse.source.browse

import android.os.Bundle
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.database.AnimeDatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Anime
import eu.kanade.tachiyomi.data.database.models.AnimeCategory
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.toAnimeInfo
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.toSAnime
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import eu.kanade.tachiyomi.ui.browse.source.filter.CheckboxItem
import eu.kanade.tachiyomi.ui.browse.source.filter.CheckboxSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.GroupItem
import eu.kanade.tachiyomi.ui.browse.source.filter.HeaderItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SelectItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SelectSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SeparatorItem
import eu.kanade.tachiyomi.ui.browse.source.filter.SortGroup
import eu.kanade.tachiyomi.ui.browse.source.filter.SortItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TextItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TextSectionItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TriStateItem
import eu.kanade.tachiyomi.ui.browse.source.filter.TriStateSectionItem
import eu.kanade.tachiyomi.util.episode.EpisodeSettingsHelper
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.removeCovers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * Presenter of [BrowseSourceController].
 */
open class BrowseAnimeSourcePresenter(
    private val sourceId: Long,
    searchQuery: String? = null,
    private val sourceManager: SourceManager = Injekt.get(),
    private val db: AnimeDatabaseHelper = Injekt.get(),
    private val prefs: PreferencesHelper = Injekt.get(),
    private val coverCache: AnimeCoverCache = Injekt.get()
) : BasePresenter<BrowseAnimeSourceController>() {

    /**
     * Selected source.
     */
    lateinit var source: AnimeCatalogueSource

    /**
     * Modifiable list of filters.
     */
    var sourceFilters = FilterList()
        set(value) {
            field = value
            filterItems = value.toItems()
        }

    var filterItems: List<IFlexible<*>> = emptyList()

    /**
     * List of filters used by the [Pager]. If empty alongside [query], the popular query is used.
     */
    var appliedFilters = FilterList()

    /**
     * Pager containing a list of anime results.
     */
    private lateinit var pager: AnimePager

    /**
     * Flow of anime list to initialize.
     */
    private val animeDetailsFlow = MutableStateFlow<List<Anime>>(emptyList())

    /**
     * Subscription for the pager.
     */
    private var pagerSubscription: Subscription? = null

    /**
     * Subscription for one request from the pager.
     */
    private var pageSubscription: Subscription? = null

    init {
        query = searchQuery ?: ""
    }

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        source = sourceManager.get(sourceId) as? AnimeCatalogueSource ?: return

        sourceFilters = source.getFilterList()

        if (savedState != null) {
            query = savedState.getString(::query.name, "")
        }

        restartPager()
    }

    override fun onSave(state: Bundle) {
        state.putString(::query.name, query)
        super.onSave(state)
    }

    /**
     * Restarts the pager for the active source with the provided query and filters.
     *
     * @param query the query.
     * @param filters the current state of the filters (for search mode).
     */
    fun restartPager(query: String = this.query, filters: FilterList = this.appliedFilters) {
        this.query = query
        this.appliedFilters = filters

        // Create a new pager.
        pager = createPager(query, filters)

        val sourceId = source.id

        val sourceDisplayMode = prefs.sourceDisplayMode()

        // Prepare the pager.
        pagerSubscription?.let { remove(it) }
        pagerSubscription = pager.results()
            .observeOn(Schedulers.io())
            .map { (first, second) -> first to second.map { networkToLocalAnime(it, sourceId) } }
            .doOnNext { initializeAnimes(it.second) }
            .map { (first, second) -> first to second.map { AnimeSourceItem(it, sourceDisplayMode) } }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeReplay(
                { view, (page, animes) ->
                    view.onAddPage(page, animes)
                },
                { _, error ->
                    Timber.e(error)
                }
            )

        // Request first page.
        requestNext()
    }

    /**
     * Requests the next page for the active pager.
     */
    fun requestNext() {
        if (!hasNextPage()) return

        pageSubscription?.let { remove(it) }
        pageSubscription = Observable.defer { pager.requestNext() }
            .subscribeFirst(
                { _, _ ->
                    // Nothing to do when onNext is emitted.
                },
                BrowseAnimeSourceController::onAddPageError
            )
    }

    /**
     * Returns true if the last fetched page has a next page.
     */
    fun hasNextPage(): Boolean {
        return pager.hasNextPage
    }

    /**
     * Returns a anime from the database for the given anime from network. It creates a new entry
     * if the anime is not yet in the database.
     *
     * @param sAnime the anime from the source.
     * @return a anime from the database.
     */
    private fun networkToLocalAnime(sAnime: SAnime, sourceId: Long): Anime {
        var localAnime = db.getAnime(sAnime.url, sourceId).executeAsBlocking()
        if (localAnime == null) {
            val newAnime = Anime.create(sAnime.url, sAnime.title, sourceId)
            newAnime.copyFrom(sAnime)
            val result = db.insertAnime(newAnime).executeAsBlocking()
            newAnime.id = result.insertedId()
            localAnime = newAnime
        }
        return localAnime
    }

    /**
     * Initialize a list of anime.
     *
     * @param animes the list of anime to initialize.
     */
    fun initializeAnimes(animes: List<Anime>) {
        presenterScope.launchIO {
            animes.asFlow()
                .filter { it.thumbnail_url == null && !it.initialized }
                .map { getAnimeDetails(it) }
                .onEach {
                    withUIContext {
                        @Suppress("DEPRECATION")
                        view?.onAnimeInitialized(it)
                    }
                }
                .catch { e -> Timber.e(e) }
                .collect()
        }
    }

    /**
     * Returns the initialized anime.
     *
     * @param anime the anime to initialize.
     * @return the initialized anime
     */
    private suspend fun getAnimeDetails(anime: Anime): Anime {
        try {
            val networkAnime = source.getAnimeDetails(anime.toAnimeInfo())
            anime.copyFrom(networkAnime.toSAnime())
            anime.initialized = true
            db.insertAnime(anime).executeAsBlocking()
        } catch (e: Exception) {
            Timber.e(e)
        }
        return anime
    }

    /**
     * Adds or removes a anime from the library.
     *
     * @param anime the anime to update.
     */
    fun changeAnimeFavorite(anime: Anime) {
        anime.favorite = !anime.favorite
        anime.date_added = when (anime.favorite) {
            true -> Date().time
            false -> 0
        }

        if (!anime.favorite) {
            anime.removeCovers(coverCache)
        } else {
            EpisodeSettingsHelper.applySettingDefaults(anime)
        }

        db.insertAnime(anime).executeAsBlocking()
    }

    /**
     * Set the filter states for the current source.
     *
     * @param filters a list of active filters.
     */
    fun setSourceFilter(filters: FilterList) {
        restartPager(filters = filters)
    }

    open fun createPager(query: String, filters: FilterList): AnimePager {
        return AnimeSourcePager(source, query, filters)
    }

    private fun FilterList.toItems(): List<IFlexible<*>> {
        return mapNotNull { filter ->
            when (filter) {
                is Filter.Header -> HeaderItem(filter)
                is Filter.Separator -> SeparatorItem(filter)
                is Filter.CheckBox -> CheckboxItem(filter)
                is Filter.TriState -> TriStateItem(filter)
                is Filter.Text -> TextItem(filter)
                is Filter.Select<*> -> SelectItem(filter)
                is Filter.Group<*> -> {
                    val group = GroupItem(filter)
                    val subItems = filter.state.mapNotNull {
                        when (it) {
                            is Filter.CheckBox -> CheckboxSectionItem(it)
                            is Filter.TriState -> TriStateSectionItem(it)
                            is Filter.Text -> TextSectionItem(it)
                            is Filter.Select<*> -> SelectSectionItem(it)
                            else -> null
                        }
                    }
                    subItems.forEach { it.header = group }
                    group.subItems = subItems
                    group
                }
                is Filter.Sort -> {
                    val group = SortGroup(filter)
                    val subItems = filter.values.map {
                        SortItem(it, group)
                    }
                    group.subItems = subItems
                    group
                }
            }
        }
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    fun getCategories(): List<Category> {
        return db.getCategories().executeAsBlocking()
    }

    /**
     * Gets the category id's the anime is in, if the anime is not in a category, returns the default id.
     *
     * @param anime the anime to get categories from.
     * @return Array of category ids the anime is in, if none returns default id
     */
    fun getAnimeCategoryIds(anime: Anime): Array<Int?> {
        val categories = db.getCategoriesForAnime(anime).executeAsBlocking()
        return categories.mapNotNull { it.id }.toTypedArray()
    }

    /**
     * Move the given anime to categories.
     *
     * @param categories the selected categories.
     * @param anime the anime to move.
     */
    private fun moveAnimeToCategories(anime: Anime, categories: List<Category>) {
        val mc = categories.filter { it.id != 0 }.map { AnimeCategory.create(anime, it) }
        db.setAnimeCategories(mc, listOf(anime))
    }

    /**
     * Move the given anime to the category.
     *
     * @param category the selected category.
     * @param anime the anime to move.
     */
    fun moveAnimeToCategory(anime: Anime, category: Category?) {
        moveAnimeToCategories(anime, listOfNotNull(category))
    }

    /**
     * Update anime to use selected categories.
     *
     * @param anime needed to change
     * @param selectedCategories selected categories
     */
    fun updateAnimeCategories(anime: Anime, selectedCategories: List<Category>) {
        if (!anime.favorite) {
            changeAnimeFavorite(anime)
        }

        moveAnimeToCategories(anime, selectedCategories)
    }
}
