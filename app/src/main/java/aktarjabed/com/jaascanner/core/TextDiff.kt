package aktarjabed.com.jaascanner.core

/** The part of a word that changed: old[start, oldEnd) is replaced by new[start, newEnd). */
data class TextChange(val start: Int, val oldEnd: Int, val newEnd: Int)

object TextDiff {

    /** Smallest single replaced range between [old] and [new]; null when they are equal. */
    fun change(old: String, new: String): TextChange? {
        if (old == new) return null
        val limit = minOf(old.length, new.length)
        var prefix = 0
        while (prefix < limit && old[prefix] == new[prefix]) prefix++
        // Never split a UTF-16 surrogate pair (e.g. changing one emoji to another that happens to
        // share the same leading surrogate): back the boundary off by one code unit if it would.
        if (prefix > 0 && prefix < old.length &&
            Character.isHighSurrogate(old[prefix - 1]) && Character.isLowSurrogate(old[prefix])
        ) prefix--
        var suffix = 0
        while (suffix < limit - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        if (suffix > 0) {
            val oldIdx = old.length - suffix
            if (oldIdx > 0 && oldIdx < old.length &&
                Character.isLowSurrogate(old[oldIdx]) && Character.isHighSurrogate(old[oldIdx - 1])
            ) suffix--
        }
        return TextChange(prefix, old.length - suffix, new.length - suffix)
    }
}
