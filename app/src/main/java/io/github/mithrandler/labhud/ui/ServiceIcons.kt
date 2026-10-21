package io.github.mithrandler.labhud.ui

import android.content.Context
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ImageRequest
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.Server
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * A card's service icon: labhud's own picture of it (/icons/svc/<name>.png, from
 * homarr-labs/dashboard-icons), else one of [LabIcons]. The pictures come from the paired server
 * only, over its pinned certificate, and are kept on disk per server.
 */
object ServiceIcons {
    private val loaders = HashMap<String, ImageLoader>()

    /** One loader per server (its own certificate, its own disk cache), made once. */
    @Synchronized
    fun loader(context: Context, server: Server): ImageLoader = loaders.getOrPut(server.id + "|" + server.fingerprint) {
        val app = context.applicationContext
        ImageLoader.Builder(app)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { LabApi.clientFor(server.fingerprint) })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(app, 0.05).build() }
            .diskCache {
                DiskCache.Builder().directory(File(app.cacheDir, "icons-" + server.id.filter { it.isLetterOrDigit() }).toOkioPath())
                    .maxSizeBytes(8L * 1024 * 1024).build()
            }
            .build()
    }

    /** labhud's line icons by the names the server may give (`icon` = server, nas, vm, pc, app…). */
    fun line(name: String): ImageVector? = when (name) {
        "server" -> LabIcons.server
        "nas" -> LabIcons.nas
        "vm" -> LabIcons.vm
        "pc" -> LabIcons.pc
        "app" -> LabIcons.app
        "status" -> LabIcons.status
        "activity" -> LabIcons.activity
        "devices" -> LabIcons.devices
        "security" -> LabIcons.security
        "sources" -> LabIcons.sources
        "bell" -> LabIcons.bell
        "note" -> LabIcons.note
        "link" -> LabIcons.link
        "update" -> LabIcons.update
        "report" -> LabIcons.report
        "bolt" -> LabIcons.bolt
        "wall" -> LabIcons.wall
        "lock" -> LabIcons.lock
        "person" -> LabIcons.person
        else -> null
    }

    /** When the card names no icon, or its picture does not load: one for what kind of thing it is. */
    fun fallback(c: Card): ImageVector = when {
        c.type == "proxmox" -> LabIcons.server
        c.type == "nas" -> LabIcons.nas
        c.type == "vm" -> LabIcons.vm
        c.type == "pc" -> LabIcons.pc
        c.type == "docker" -> LabIcons.app
        c.notes != null -> LabIcons.note
        c.bookmarks.isNotEmpty() -> LabIcons.link
        c.updates != null -> LabIcons.update
        c.kind == "list" -> LabIcons.report
        else -> LabIcons.status
    }
}

/** The card's icon at [size]: the server's picture of the service when it has one, else a line icon. */
@Composable
fun CardIcon(c: Card, server: Server?, size: Dp = LabScale.iconCard, tint: Color = Lab.sub) {
    val line = ServiceIcons.line(c.icon)
    var failed by remember(c.icon, server?.id) { mutableStateOf(false) }
    if (line != null || c.icon.isEmpty() || server == null || failed) {
        Icon(line ?: ServiceIcons.fallback(c), null, tint = tint, modifier = Modifier.size(size))
        return
    }
    val context = LocalContext.current
    val loader = remember(server.id, server.fingerprint) { ServiceIcons.loader(context, server) }
    val request = remember(c.icon, server.url) {
        ImageRequest.Builder(context).data(server.url.trimEnd('/') + "/icons/svc/" + android.net.Uri.encode(c.icon) + ".png").build()
    }
    AsyncImage(request, contentDescription = null, imageLoader = loader, modifier = Modifier.size(size), onError = { failed = true })
}
