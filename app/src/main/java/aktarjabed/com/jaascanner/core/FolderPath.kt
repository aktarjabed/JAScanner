package aktarjabed.com.jaascanner.core

import aktarjabed.com.jaascanner.data.db.FolderEntity

/** Human-readable folder paths such as "Clients / 2026 / March". Pure Kotlin, so it is unit-tested. */
object FolderPath {

    fun label(folders: List<FolderEntity>, id: Long): String {
        val byId = folders.associateBy { it.id }
        val parts = ArrayList<String>()
        var current: FolderEntity? = byId[id]
        val seen = HashSet<Long>()
        while (current != null && seen.add(current.id)) {
            parts.add(0, current.name)
            current = current.parentId?.let { byId[it] }
        }
        return parts.joinToString(" / ").ifEmpty { "Folder" }
    }

    /** Ids of [id] and every folder below it. Used to show a folder's subtree in pickers. */
    fun descendants(folders: List<FolderEntity>, id: Long): Set<Long> {
        val result = mutableSetOf(id)
        var grew = true
        while (grew) {
            grew = false
            for (folder in folders) {
                val parent = folder.parentId ?: continue
                if (parent in result && result.add(folder.id)) grew = true
            }
        }
        return result
    }
}
