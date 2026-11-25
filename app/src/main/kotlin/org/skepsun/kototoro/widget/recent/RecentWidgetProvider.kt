package org.skepsun.kototoro.widget.recent

import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.SizeMode
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.BaseApp
import org.skepsun.kototoro.core.db.MangaDatabase
import org.skepsun.kototoro.core.nav.AppRouter
import org.skepsun.kototoro.core.ui.compose.contentCoverCacheKey
import org.skepsun.kototoro.core.prefs.AppWidgetConfig
import org.skepsun.kototoro.core.util.ext.getDrawableOrThrow
import org.skepsun.kototoro.core.util.ext.mangaExtra
import org.skepsun.kototoro.home.ui.compose.homeMediaKind
import org.skepsun.kototoro.main.ui.MainActivity
import org.skepsun.kototoro.parsers.model.Content
import org.skepsun.kototoro.parsers.util.runCatchingCancellable
import org.skepsun.kototoro.widget.WidgetEmptyState
import org.skepsun.kototoro.widget.WidgetHeader
import org.skepsun.kototoro.widget.WidgetPrimaryTextColor
import org.skepsun.kototoro.widget.WidgetSecondaryTextColor
import org.skepsun.kototoro.widget.widgetRootModifier
import java.text.NumberFormat

private const val WIDGET_ITEM_LIMIT = 10
private const val COVER_CACHE_READ_TIMEOUT_MILLIS = 5_000L

class RecentWidgetProvider : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = RecentGlanceWidget

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        copyConfigs(context, oldWidgetIds, newWidgetIds, RecentWidgetProvider::class.java)
    }
}

private fun copyConfigs(
    context: Context,
    oldWidgetIds: IntArray,
    newWidgetIds: IntArray,
    providerClass: Class<out AppWidgetProvider>,
) {
    if (oldWidgetIds.size != newWidgetIds.size) return
    for (index in oldWidgetIds.indices) {
        val oldConfig = AppWidgetConfig(context, providerClass, oldWidgetIds[index])
        val newConfig = AppWidgetConfig(context, providerClass, newWidgetIds[index])
        newConfig.copyFrom(oldConfig)
        oldConfig.clear()
    }
}

private object RecentGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = AppWidgetConfig(context, RecentWidgetProvider::class.java, appWidgetId)
        val items = runCatchingCancellable {
            val entryPoint = EntryPointAccessors.fromApplication<BaseApp.BaseAppEntryPoint>(context.applicationContext)
            loadItems(context, entryPoint.historyRepository(), entryPoint.imageLoader(), entryPoint.settings(), entryPoint.database().get())
        }.getOrDefault(emptyList())
        provideContent {
            RecentWidgetContent(
                items = items,
                hasBackground = config.hasBackground,
                title = context.getString(R.string.appwidget_resume_title),
                emptyText = context.getString(R.string.history_is_empty),
            )
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        AppWidgetConfig(context, RecentWidgetProvider::class.java, appWidgetId).clear()
        super.onDelete(context, glanceId)
    }

    private suspend fun loadItems(
        context: Context,
        historyRepository: org.skepsun.kototoro.history.data.HistoryRepository,
        imageLoader: ImageLoader,
        settings: org.skepsun.kototoro.core.prefs.AppSettings,
        database: MangaDatabase,
    ): List<RecentWidgetItem> = withContext(Dispatchers.IO) {
        if (!settings.appPassword.isNullOrEmpty() || settings.isIncognitoModeEnabled) {
            return@withContext emptyList()
        }
        val items = historyRepository.observeRecentWithHistory(WIDGET_ITEM_LIMIT).first()
        val chapters = database.getChaptersDao().findByIds(items.map { it.history.parentChapterId ?: it.history.chapterId })
            .associateBy { it.mangaId to it.chapterId }
        coroutineScope {
            items.map { (content, history) ->
                async {
                    runCatchingCancellable {
                        val chapter = chapters[content.id to (history.parentChapterId ?: history.chapterId)]
                        val isVideo = content.homeMediaKind().isVideo
                        val position = if (isVideo) {
                            val seconds = history.page.coerceAtLeast(0) / 1000
                            context.getString(R.string.media_home_video_position, seconds / 60, seconds % 60)
                        } else {
                            context.getString(R.string.home_resume_progress, (history.percent.coerceIn(0f, 1f) * 100).toInt())
                        }
                        val label = chapter?.number?.takeIf { it.isFinite() && it > 0f }?.let {
                            context.getString(
                                if (isVideo) R.string.appwidget_resume_episode else R.string.chapter_number,
                                NumberFormat.getNumberInstance().format(it),
                            )
                        } ?: chapter?.title?.takeIf(String::isNotBlank)
                        RecentWidgetItem(
                            id = content.id,
                            title = content.title,
                            metadata = label?.let { "$it · $position" } ?: position,
                            cover = loadCover(context, imageLoader, content),
                            readerIntent = Intent(context, MainActivity::class.java)
                                .setData(AppRouter.shortContentUrl(content.id))
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra(MainActivity.EXTRA_WIDGET_RESUME, content.id),
                        )
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun loadCover(context: Context, imageLoader: ImageLoader, content: Content): Bitmap? {
        val coverUrl = content.coverUrl?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("//")) "https:$it" else it } ?: return null
        val cacheKey = contentCoverCacheKey(content, coverUrl) ?: return null
        return withTimeoutOrNull(COVER_CACHE_READ_TIMEOUT_MILLIS) {
            runCatchingCancellable {
                imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(coverUrl)
                        .allowHardware(false)
                        .memoryCacheKey(cacheKey)
                        .diskCacheKey(cacheKey)
                        .mangaExtra(content)
                        .diskCachePolicy(CachePolicy.READ_ONLY)
                        .networkCachePolicy(CachePolicy.DISABLED)
                        .build(),
                ).getDrawableOrThrow().toBitmap()
            }.getOrNull()
        }
    }
}

private data class RecentWidgetItem(
    val id: Long,
    val title: String,
    val metadata: String,
    val cover: Bitmap?,
    val readerIntent: Intent,
)

@Composable
private fun RecentWidgetContent(
    items: List<RecentWidgetItem>,
    hasBackground: Boolean,
    title: String,
    emptyText: String,
) {
    val rootModifier = widgetRootModifier(hasBackground)
    if (items.isEmpty()) {
        WidgetEmptyState(text = emptyText, modifier = rootModifier)
    } else {
        val showHeader = hasBackground && LocalSize.current.height >= 140.dp
        Column(modifier = rootModifier) {
            if (showHeader) {
                WidgetHeader(title = title, itemCount = items.size)
            }
            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(
                    items = items,
                ) { item ->
                    RecentCard(item)
                }
            }
        }
    }
}

@Composable
private fun RecentCard(item: RecentWidgetItem) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(4.dp)
            .background(ImageProvider(R.drawable.bg_appwidget_card))
            .cornerRadius(R.dimen.appwidget_corner_radius_inner)
            .clickable(actionStartActivity(item.readerIntent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = item.cover?.let { ImageProvider(it) } ?: ImageProvider(R.drawable.ic_placeholder),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier
                .width(64.dp)
                .height(88.dp),
        )
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.title,
                maxLines = 2,
                style = TextStyle(
                    color = WidgetPrimaryTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = item.metadata,
                maxLines = 1,
                style = TextStyle(
                    color = WidgetSecondaryTextColor,
                    fontSize = 12.sp,
                ),
                modifier = GlanceModifier.padding(top = 5.dp),
            )
        }
    }
}

@Preview
@Composable
private fun RecentWidgetContentPreview() {
    RecentWidgetContent(items = emptyList(), hasBackground = true, title = "Recent", emptyText = "No history")
}
