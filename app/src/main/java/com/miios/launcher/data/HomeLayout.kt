package com.miios.launcher.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One place on the home screen: a channel, or a named folder of channels. */
sealed interface Slot {
    val key: String
    val label: String

    data class App(val app: InstalledApp) : Slot {
        override val key get() = app.packageName
        override val label get() = app.label
    }

    data class Folder(val id: String, val name: String, val apps: List<InstalledApp>) : Slot {
        override val key get() = FOLDER + id
        override val label get() = name
    }

    companion object {
        const val FOLDER = "folder:"
    }
}

/**
 * The owner's own arrangement of the home screen: channel order and folders. Saved on the box only.
 * New apps that aren't placed yet are added at the end, in the default order (favourites first).
 */
class HomeLayout(context: Context) {

    private val prefs = context.getSharedPreferences("home_layout", Context.MODE_PRIVATE)

    private data class FolderData(val name: String, val apps: List<String>)

    private fun order(): List<String> = prefs.getString("order", null)?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()

    private fun folders(): LinkedHashMap<String, FolderData> {
        val out = LinkedHashMap<String, FolderData>()
        val json = prefs.getString("folders", null) ?: return out
        runCatching {
            val obj = JSONObject(json)
            for (id in obj.keys()) {
                val f = obj.getJSONObject(id)
                val apps = f.getJSONArray("apps")
                out[id] = FolderData(f.getString("name"), List(apps.length()) { apps.getString(it) })
            }
        }
        return out
    }

    private fun save(order: List<String>, folders: Map<String, FolderData>) {
        val obj = JSONObject()
        for ((id, f) in folders) obj.put(id, JSONObject().put("name", f.name).put("apps", JSONArray(f.apps)))
        prefs.edit().putString("order", order.joinToString("\n")).putString("folders", obj.toString()).apply()
    }

    /** Builds the home screen from the installed apps (already in default order). */
    fun arrange(apps: List<InstalledApp>): List<Slot> {
        val byPkg = apps.associateBy { it.packageName }
        val folders = folders()
        val inFolders = folders.values.flatMap { it.apps }.toSet()
        val slots = ArrayList<Slot>()
        val placed = HashSet<String>()
        fun folderSlot(id: String): Slot? {
            val f = folders[id] ?: return null
            val content = f.apps.mapNotNull { byPkg[it] }
            return Slot.Folder(id, f.name, content)
        }
        for (key in order()) {
            if (!placed.add(key)) continue
            if (key.startsWith(Slot.FOLDER)) {
                folderSlot(key.removePrefix(Slot.FOLDER))?.let { slots += it }
            } else if (key !in inFolders) {
                byPkg[key]?.let { slots += Slot.App(it) }
            }
        }
        // folders the order doesn't know yet, then new apps
        for (id in folders.keys) if (placed.add(Slot.FOLDER + id)) folderSlot(id)?.let { slots += it }
        for (app in apps) if (app.packageName !in inFolders && placed.add(app.packageName)) slots += Slot.App(app)
        return slots
    }

    /** Saves the order of the home screen as shown. */
    fun saveOrder(slots: List<Slot>) = save(slots.map { it.key }, folders())

    /** Makes a folder holding [pkg], in [pkg]'s place on the home screen. Returns the new folder's id. */
    fun newFolder(slots: List<Slot>, pkg: String, name: String): String {
        val folders = folders()
        val id = System.currentTimeMillis().toString(36)
        folders[id] = FolderData(name, listOf(pkg))
        save(slots.map { if (it.key == pkg) Slot.FOLDER + id else it.key }, folders)
        return id
    }

    fun addToFolder(slots: List<Slot>, pkg: String, folderId: String) {
        val folders = folders()
        val f = folders[folderId] ?: return
        folders[folderId] = f.copy(apps = f.apps.filter { it != pkg } + pkg)
        save(slots.map { it.key }.filter { it != pkg }, folders)
    }

    /** Takes [pkg] out of its folder and puts it right after the folder on the home screen. */
    fun removeFromFolder(slots: List<Slot>, pkg: String, folderId: String) {
        val folders = folders()
        val f = folders[folderId] ?: return
        folders[folderId] = f.copy(apps = f.apps.filter { it != pkg })
        val order = slots.map { it.key }.toMutableList()
        val at = order.indexOf(Slot.FOLDER + folderId)
        order.add(if (at >= 0) at + 1 else order.size, pkg)
        save(order, folders)
    }

    fun renameFolder(folderId: String, name: String) {
        val folders = folders()
        val f = folders[folderId] ?: return
        folders[folderId] = f.copy(name = name)
        save(order(), folders)
    }

    /** Deletes the folder; its channels go back onto the home screen where the folder was. */
    fun deleteFolder(slots: List<Slot>, folderId: String) {
        val folders = folders()
        val f = folders.remove(folderId) ?: return
        val order = slots.flatMap { if (it.key == Slot.FOLDER + folderId) f.apps else listOf(it.key) }
        save(order, folders)
    }
}
