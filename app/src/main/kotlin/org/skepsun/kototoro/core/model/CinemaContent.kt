package org.skepsun.kototoro.core.model

import org.skepsun.kototoro.core.video.CinemaKind
import org.skepsun.kototoro.parsers.model.Content

internal fun Content.cinemaKind(): CinemaKind? = CinemaKind.fromSourceData(source.name, sourceData)
