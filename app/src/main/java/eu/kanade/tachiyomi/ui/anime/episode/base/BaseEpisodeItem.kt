package eu.kanade.tachiyomi.ui.anime.episode.base

import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.kanade.tachiyomi.data.database.models.Episode
import eu.kanade.tachiyomi.data.download.model.AnimeDownload

abstract class BaseEpisodeItem<T : BaseEpisodeHolder, H : AbstractHeaderItem<*>>(
    val episode: Episode,
    header: H? = null,
) :
    AbstractSectionableItem<T, H?>(header),
    Episode by episode {

    private var _status: AnimeDownload.State = AnimeDownload.State.NOT_DOWNLOADED

    var status: AnimeDownload.State
        get() = download?.status ?: _status
        set(value) {
            _status = value
        }

    val progress: Int
        get() {
            val video = download?.video ?: return 0
            return video.progress
        }

    @Transient
    var download: AnimeDownload? = null

    val isDownloaded: Boolean
        get() = status == AnimeDownload.State.DOWNLOADED

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is BaseEpisodeItem<*, *>) {
            return episode.id!! == other.episode.id!!
        }
        return false
    }

    override fun hashCode(): Int {
        return episode.id!!.hashCode()
    }
}
