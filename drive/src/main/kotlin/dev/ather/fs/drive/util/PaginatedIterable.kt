package dev.ather.fs.drive.util

internal data class PaginatedIterable<T>(
    val initialValues: List<T> = emptyList(),
    val getNextPage: (String?) -> PaginatedIterableResult<T>?
) : Iterable<T> {

    private var previousResult: PaginatedIterableResult<T>? = null
    private var currentIndex = 0

    private fun paginate() {
        val result = getNextPage(previousResult?.nextPageToken)
        previousResult = result
        currentIndex = 0
    }

    override fun iterator(): Iterator<T> = object : Iterator<T> {

        private fun paginatedNext() = paginate().run { next() }

        private fun paginatedHasNext() = paginate().run { previousResult?.result?.isNotEmpty() ?: false }

        override fun hasNext(): Boolean = previousResult?.let { previousResult ->
            currentIndex < previousResult.result.size // there are still values left in the current response
                    || (previousResult.nextPageToken != null && paginatedHasNext()) // there are more values to paginate
        } ?: (currentIndex < initialValues.size // we are in initialValues
                || paginatedHasNext()) // no request has been made yet, and we don't know if it will contain values (so we have to request the next page)

        override fun next(): T = previousResult?.let { previousResult ->
            when {
                currentIndex < previousResult.result.size -> previousResult.result[currentIndex++]
                previousResult.nextPageToken != null -> paginatedNext()
                else -> throw NoSuchElementException()
            }
        } ?: when {
            currentIndex < initialValues.size -> initialValues[currentIndex++]
            else -> paginatedNext()
        }
    }

    data class PaginatedIterableResult<T>(
        val result: List<T>,
        val nextPageToken: String?
    )
}
