package com.harsh.desicrics.stats

/**
 * `java.util.Map.merge` for every target: puts [value], or combines it with what's there. On the JVM the
 * member function wins and this is never called; the web app's JS build needs it.
 */
internal fun <K, V> MutableMap<K, V>.merge(key: K, value: V, combine: (V, V) -> V) {
    val old = get(key)
    put(key, if (old == null) value else combine(old, value))
}
