package io.github.andreygladkov.inklauncher.home

import org.json.JSONArray
import org.json.JSONObject

object LayoutJson {
    private const val VERSION = 1

    fun write(layout: HomeLayout): String = JSONObject()
        .put("version", VERSION)
        .put("spec", JSONObject()
            .put("columns", layout.spec.columns)
            .put("rows", layout.spec.rows)
            .put("pinnedTop", layout.spec.pinnedTop)
            .put("pinnedBottom", layout.spec.pinnedBottom))
        .put("pages", layout.pageCount)
        .put("tiles", JSONArray(layout.placed.map { placed -> spot(placed.spot, tile(placed.tile)) }))
        .toString()

    fun read(text: String): HomeLayout {
        val json = JSONObject(text)
        val spec = json.getJSONObject("spec").let {
            GridSpec(it.getInt("columns"), it.getInt("rows"), it.getInt("pinnedTop"), it.getInt("pinnedBottom"))
        }
        val tiles = json.getJSONArray("tiles")
        val placed = (0 until tiles.length()).mapNotNull { index ->
            val item = tiles.getJSONObject(index)
            tile(item)?.let { Placed(it, spot(item)) }
        }
        return HomeLayout(spec, json.getInt("pages"), placed)
    }

    private fun spot(spot: Spot, into: JSONObject): JSONObject = into
        .put("sheet", when (spot.sheet) {
            Sheet.Top -> "top"
            Sheet.Bottom -> "bottom"
            is Sheet.Page -> "page"
        })
        .put("page", (spot.sheet as? Sheet.Page)?.index ?: 0)
        .put("column", spot.column)
        .put("row", spot.row)
        .put("width", spot.width)
        .put("height", spot.height)

    private fun spot(json: JSONObject) = Spot(
        sheet = when (json.getString("sheet")) {
            "top" -> Sheet.Top
            "bottom" -> Sheet.Bottom
            else -> Sheet.Page(json.getInt("page"))
        },
        column = json.getInt("column"),
        row = json.getInt("row"),
        width = json.getInt("width"),
        height = json.getInt("height"),
    )

    private fun tile(tile: Tile): JSONObject = when (tile) {
        is AppTile -> labeled(tile, JSONObject().put("type", "app").put("app", tile.app.flatten()))
        is FolderTile -> labeled(tile, JSONObject().put("type", "folder").put("apps", JSONArray(tile.apps.map(::tile))))
        is WidgetTile -> JSONObject().put("type", "widget").put("id", tile.id).put("widgetId", tile.widgetId).put("provider", tile.provider.flatten())
            .put("monochrome", tile.monochrome)
        is ScreenTimeTile -> JSONObject().put("type", "screen_time").put("id", tile.id)
    }

    private fun labeled(tile: LabeledTile, into: JSONObject): JSONObject = into
        .put("id", tile.id)
        .putOpt("label", tile.label)
        .putOpt("icon", tile.icon?.let(::icon))
        .put("labelHidden", tile.labelHidden)

    private fun tile(json: JSONObject): Tile? {
        val id = json.getLong("id")
        val label = json.optString("label").takeIf { json.has("label") }
        val icon = json.optJSONObject("icon")?.let(::icon)
        val hidden = json.optBoolean("labelHidden")
        return when (json.getString("type")) {
            "app" -> AppKey.unflatten(json.getString("app"))?.let { AppTile(id, it, label, icon, hidden) }
            "folder" -> {
                val apps = json.getJSONArray("apps")
                FolderTile(id, label.orEmpty(), (0 until apps.length()).mapNotNull { tile(apps.getJSONObject(it)) as? AppTile }, icon, hidden)
            }
            "widget" -> AppKey.unflatten(json.getString("provider"))?.let { WidgetTile(id, json.getInt("widgetId"), it, json.optBoolean("monochrome")) }
            "screen_time" -> ScreenTimeTile(id)
            else -> null
        }
    }

    private fun icon(icon: CustomIcon): JSONObject = when (icon) {
        is CustomIcon.Picture -> JSONObject().put("type", "picture").put("file", icon.file)
        is CustomIcon.Text -> JSONObject().put("type", "text").put("text", icon.text)
        is CustomIcon.FromPack -> JSONObject().put("type", "pack").put("pack", icon.pack).put("drawable", icon.drawable)
    }

    private fun icon(json: JSONObject): CustomIcon? = when (json.getString("type")) {
        "picture" -> CustomIcon.Picture(json.getString("file"))
        "text" -> CustomIcon.Text(json.getString("text"))
        "pack" -> CustomIcon.FromPack(json.getString("pack"), json.getString("drawable"))
        else -> null
    }
}
