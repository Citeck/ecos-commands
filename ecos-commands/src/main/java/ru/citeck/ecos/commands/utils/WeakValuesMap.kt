package ru.citeck.ecos.commands.utils

import mu.KotlinLogging
import java.lang.ref.Reference
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WeakValuesMap<K, V> {

    companion object {
        private val log = KotlinLogging.logger {}
    }

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

        for (i in 0..1) {
            val oldRef = refsQueue.poll() ?: break
            val oldKey = keyByRef.remove(oldRef)
            if (oldKey != null) {
                log.debug { "Remove old key by refsQueue: $oldKey" }
                data.remove(oldKey)
            }
        }

        val ref = WeakReference(value, refsQueue)
        data[key] = ref
        keyByRef[ref] = key
    }

    @Synchronized
    fun get(key: K): V? {
        val weakRef = data[key] ?: return null
        val result = weakRef.get()
        return if (result == null) {
            log.debug { "Ref was found, but referenced value already deleted by GC. Key: $key" }
            remove(key)
            null
        } else {
            result
        }
    }

    @Synchronized
    fun size(): Int {
        return data.size
    }
}
