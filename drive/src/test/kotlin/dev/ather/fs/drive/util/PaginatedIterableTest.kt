package dev.ather.fs.drive.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class PaginatedIterableTest {

    @Test
    fun `initialValues only`() {
        val iterable = PaginatedIterable(
            listOf("test", "test1")
        ) { null }
        val list = iterable.toList()

        assertEquals(2, list.size)
        assertEquals("test", list[0])
        assertEquals("test1", list[1])
    }

    @Test
    fun `no values`() {
        val iterable = PaginatedIterable<Any> { null }
        val list = iterable.toList()

        assertEquals(0, list.size)
    }

    @Test
    fun `no initialValues, 1 page`() {
        val iterable = PaginatedIterable {
            PaginatedIterable.PaginatedIterableResult(listOf("1"), null)
        }
        val list = iterable.toList()

        assertEquals(1, list.size)
        assertEquals("1", list[0])
    }

    @Test
    fun `no initialValues, multiple pages`() {
        val iterable = PaginatedIterable {
            when (it) {
                null -> PaginatedIterable.PaginatedIterableResult(listOf("test1", "test2", "test3"), "t1")
                "t1" -> PaginatedIterable.PaginatedIterableResult(listOf("test4", "test5", "test6"), null)
                else -> throw IllegalStateException()
            }
        }
        val list = iterable.toList()

        assertEquals(6, list.size)
        assertEquals(
            listOf("test1", "test2", "test3", "test4", "test5", "test6"),
            list
        )
    }

    @Test
    fun `no initialValues, 1 page, 1 empty page`() {
        val iterable = PaginatedIterable {
            when (it) {
                null -> PaginatedIterable.PaginatedIterableResult(listOf("test1", "test2", "test3"), "t1")
                "t1" -> PaginatedIterable.PaginatedIterableResult(emptyList(), null)
                else -> throw IllegalStateException()
            }
        }
        val list = iterable.toList()

        assertEquals(3, list.size)
        assertEquals(
            listOf("test1", "test2", "test3"),
            list
        )
    }

    @Test
    fun `initialValues, multiple pages`() {
        val iterable = PaginatedIterable(
            listOf("test1", "test2", "test3")
        ) {
            when (it) {
                null -> PaginatedIterable.PaginatedIterableResult(listOf("test4", "test5", "test6"), "t1")
                "t1" -> PaginatedIterable.PaginatedIterableResult(listOf("test7", "test8", "test9"), null)
                else -> throw IllegalStateException()
            }
        }
        val list = iterable.toList()

        assertEquals(9, list.size)
        assertEquals(
            listOf("test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9"),
            list
        )
    }
}
