package ru.citeck.ecos.commands.utils

import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WeakValuesMap<K, V> {

    private val data: MutableMap<K, WeakReference<V>> = ConcurrentHashMap()
    private val keyByRef: MutableMap<Reference<V>, K> = IdentityHashMap()

    private val refsQueue = ReferenceQueue<V>()

    @Synchronized
    fun remove(key: K) {
        val value = data.remove(key)
        if (value != null) {
            keyByRef.remove(value)
        }
    }

    @Synchronized
    fun put(key: K, value: V) {

        val oldRef = refsQueue.poll()
        if (oldRef != null) {
            val oldKey = keyByRef.remove(oldRef)
            if (oldKey != null) {
                data.remove(oldKey)
            }
        }

        val ref = WeakReference(value, refsQueue)
        data[key] = ref
        keyByRef[ref] = key
    }

    @Synchronized
    fun get(key: K) : V? {
        val weakRef = data[key] ?: return null
        val result = weakRef.get()
        return if (result == null) {
            remove(key)
            null
        } else {
            result
        }
    }

    @Synchronized
    fun size() : Int {
        return data.size
    }
}
