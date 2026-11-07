package io.github.andreygladkov.inklauncher.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inklauncher.LauncherPrefs
import io.github.andreygladkov.inklauncher.apps.AppCatalog
import io.github.andreygladkov.inklauncher.home.AppKey
import io.github.andreygladkov.inklauncher.home.AppTile
import io.github.andreygladkov.inklauncher.home.CustomIcon
import io.github.andreygladkov.inklauncher.home.FolderTile
import io.github.andreygladkov.inklauncher.home.LabeledTile
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * An app's icon is the first of: the one set by hand, the selected pack's, the app's themed monochrome layer,
 * its ordinary icon dithered. Rendered icons are kept on disk, keyed by the package's update time.
 */
class IconRepository(private val context: Context, private val catalog: AppCatalog) {
    private val prefs = context.inkPrefs()
    private val density = context.resources.displayMetrics.density
    private val disk = File(context.cacheDir, DISK_DIRECTORY)
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val mappings = PackMappings(File(context.cacheDir, MAPPINGS_DIRECTORY))
    private val loader = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var mapping: Map<AppKey, String> = emptyMap()
    private var mappingFor: String? = null
    private var parsingFor: String? = null

    /** Called on the main thread once the selected pack has been read in the background and its icons can show. */
    var onPackLoaded: (() -> Unit)? = null

    init {
        if ((disk.list()?.size ?: 0) > MAX_DISK_FILES) disk.deleteRecursively()
        disk.mkdirs()
    }

    val selectedPack: String? get() = prefs.getString(LauncherPrefs.ICON_PACK, null)

    fun isInverted(pack: String) = prefs.getBoolean(LauncherPrefs.packInvertKey(pack), false)

    fun tile(tile: LabeledTile, sizePx: Int, style: InkStyle, fallbackLabel: String): Bitmap = when (tile) {
        is AppTile -> app(tile.app, tile.icon, sizePx, style, fallbackLabel)
        is FolderTile -> tile.icon?.let { custom(it, sizePx, style) } ?: folder(tile, sizePx, style)
    }

    val isFramed: Boolean get() = prefs.getBoolean(LauncherPrefs.ICON_FRAME, false)

    /** Text icons and folders always have a frame of their own, so only pictures get the optional one. */
    fun app(
        key: AppKey,
        custom: CustomIcon?,
        sizePx: Int,
        style: InkStyle,
        fallbackLabel: String = catalog.label(key),
        framed: Boolean = isFramed,
    ): Bitmap {
        if (custom is CustomIcon.Text) return text(custom.text, sizePx, style)
        val inner = if (framed) InkIcons.framedSize(sizePx, linePx()) else sizePx
        val picture = custom?.let { custom(it, inner, style) } ?: fromSelectedPack(key, inner) ?: system(key, inner)
            ?: return text(fallbackLabel.take(1).uppercase(), sizePx, style)
        return if (framed) InkIcons.framed(sizePx, linePx(), style.cornerRadiusDp * density, picture) else picture
    }

    fun packIcon(pack: String, drawable: String, sizePx: Int): Bitmap? {
        val invert = isInverted(pack)
        return cached("pack:$pack@${catalog.version(pack)}:$drawable:$invert:$sizePx") {
            IconPack.load(context, pack)?.drawable(drawable)?.let { InkIcons.dithered(it, sizePx, invert) }
        }
    }

    fun text(text: String, sizePx: Int, style: InkStyle): Bitmap {
        val key = "text:$text:$sizePx:${style.cornerRadiusDp}"
        return memory.get(key) ?: InkIcons.text(text, sizePx, { StemFit.typeface(it, InkStyle.BOLD_WEIGHT) }, linePx(), style.cornerRadiusDp * density)
            .also { memory.put(key, it) }
    }

    fun forgetMemory() = memory.evictAll()

    private fun folder(folder: FolderTile, sizePx: Int, style: InkStyle): Bitmap {
        val mini = InkIcons.folderCellSize(sizePx, linePx())
        val minis = folder.apps.take(FOLDER_PREVIEW).map { app(it.app, it.icon, mini, style, framed = false) }
        return InkIcons.folder(sizePx, linePx(), style.cornerRadiusDp * density, minis)
    }

    private fun custom(icon: CustomIcon, sizePx: Int, style: InkStyle): Bitmap? = when (icon) {
        is CustomIcon.Picture -> cached("picture:${icon.file}:$sizePx") {
            CustomPictures.load(context, icon.file)?.let { InkIcons.picture(it, sizePx) }
        }
        is CustomIcon.Text -> text(icon.text, sizePx, style)
        is CustomIcon.FromPack -> packIcon(icon.pack, icon.drawable, sizePx)
    }

    private fun fromSelectedPack(key: AppKey, sizePx: Int): Bitmap? {
        val pack = selectedPack ?: return null
        val drawable = packDrawable(pack, key) ?: return null
        return packIcon(pack, drawable, sizePx)
    }

    /**
     * Reading a whole pack takes seconds for the big ones, so the main thread only ever reads the saved mapping
     * of installed apps. An app missing from it sends the pack to be read in the background, and its own icon
     * shows meanwhile.
     */
    private fun packDrawable(pack: String, key: AppKey): String? {
        val version = catalog.version(pack)
        val id = "$pack@$version"
        if (mappingFor != id) {
            mapping = mappings.read(pack, version).orEmpty()
            mappingFor = id
        }
        mapping[key]?.let { return it.ifEmpty { null } }
        val loaded = IconPack.load(context, pack) ?: return null
        if (loaded.isParsed) return loaded.drawableFor(key)
        parseInBackground(pack, version, id)
        return null
    }

    private fun parseInBackground(pack: String, version: Long, id: String) {
        if (parsingFor == id) return
        parsingFor = id
        val apps = catalog.apps().map { it.key }
        loader.execute {
            val iconPack = IconPack.load(context, pack) ?: return@execute
            val parsed = apps.associateWith { iconPack.drawableFor(it).orEmpty() }
            runCatching { mappings.write(pack, version, parsed) }
            main.post {
                parsingFor = null
                mapping = parsed
                mappingFor = id
                onPackLoaded?.invoke()
            }
        }
    }

    private fun system(key: AppKey, sizePx: Int): Bitmap? = cached("app:${key.flatten()}@${catalog.version(key.packageName)}:$sizePx") {
        val drawable = catalog.icon(key)
        InkIcons.monochromeLayer(drawable, sizePx) ?: drawable?.let { InkIcons.dithered(it, sizePx) }
    }

    private fun linePx() = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)

    private fun memory(key: String, render: () -> Bitmap?): Bitmap? = memory.get(key) ?: render()?.also { memory.put(key, it) }

    private fun cached(key: String, render: () -> Bitmap?): Bitmap? = memory(key) {
        val file = File(disk, digest(key) + ".png")
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inScaled = false })?.apply { density = Bitmap.DENSITY_NONE }
            ?: render()?.also { bitmap -> runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
    }

    private fun digest(key: String) = MessageDigest.getInstance("SHA-1").digest(key.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val DISK_DIRECTORY = "icons"
        const val MAPPINGS_DIRECTORY = "pack-mappings"
        const val MAX_DISK_FILES = 4000
        const val MEMORY_BYTES = 8 * 1024 * 1024
        const val FOLDER_PREVIEW = 4
    }
}
