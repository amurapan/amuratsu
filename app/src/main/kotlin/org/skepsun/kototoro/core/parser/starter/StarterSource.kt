package org.skepsun.kototoro.core.parser.starter

import androidx.annotation.StringRes
import org.skepsun.kototoro.R
import org.skepsun.kototoro.parsers.model.ContentSource
import org.skepsun.kototoro.parsers.model.ContentType

enum class StarterSource(
    override val contentType: ContentType,
    @StringRes val titleRes: Int,
) : ContentSource {
    AMURATSU_STARTER_MANGA(ContentType.MANGA, R.string.starter_manga),
    AMURATSU_STARTER_BOOKS(ContentType.NOVEL, R.string.starter_books),
    AMURATSU_STARTER_ANIME(ContentType.VIDEO, R.string.starter_anime),
    AMURATSU_STARTER_MOVIES(ContentType.VIDEO, R.string.starter_movies),
    AMURATSU_STARTER_SERIES(ContentType.VIDEO, R.string.starter_series),
    ;

    override val locale: String get() = ""
}
