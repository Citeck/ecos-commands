package ru.citeck.ecos.commands.test

import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.utils.WeakValuesMap
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WeakValuesMapTest {

    //potentially not stable test
    //@Test
    fun test() {

        val weakMap = WeakValuesMap<String, TestClass>()

        val testKey = "TestKey"
        weakMap.put(testKey, TestClass())

        assertEquals(1, weakMap.size())
        assertNotNull(weakMap.get(testKey))

        val testKey2 = testKey + 2
        weakMap.put(testKey2, TestClass())
        assertEquals(2, weakMap.size())
        assertNotNull(weakMap.get(testKey))
        assertNotNull(weakMap.get(testKey2))

        System.gc()
        System.gc()

        val testKey3 = testKey + 3
        weakMap.put(testKey3, TestClass())
        //one was removed in put
        assertEquals(2, weakMap.size())

        assertNull(weakMap.get(testKey))
        assertNull(weakMap.get(testKey2))

        assertEquals(1, weakMap.size())
    }

    data class TestClass(
        val field0: String = "21312",
        val field1: Int = 21312
    )
}
