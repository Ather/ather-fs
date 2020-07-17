package dev.ather.fs.drive

import java.net.URI
import java.nio.file.*

/**
 * Google Drive implementation of [Path]
 *
 * Represent Google Drive files as
 * ```
 * drive:/[rootId][/path][?credentialId]
 * ```
 *
 * Where
 *
 *      | Name         | Description                                                                            |
 *      |--------------|----------------------------------------------------------------------------------------|
 *      | rootId       | The root of the path. This can be a folder ID, file ID, or "root".                     |
 *      |              | If a file ID, the path is absolute and cannot have additional siblings resolved to it. |
 *      |              | If null, the path is not treated as absolute.                                          |
 *      | path         | The "/" delimited list of folder/file names relative to the root fileId                |                                                                                                                              |
 *      | credentialId | The credentialId to use while looking up credentials to access a given file.           |
 *      |              | null can be used if there is only a single credential which needs to be supported.     |
 *
 * @see Path
 */
data class DrivePath(
    private val driveFileSystem: DriveFileSystem,
    private val credentialId: String?,
    private val rootId: String? = "root",
    private val elements: List<String>
) : Path {

    private val normalizedElements: List<String> by lazy {
        mutableListOf<String>().apply {
            elements.forEach { element ->
                if (element == ".." && isNotEmpty()) {
                    removeAt(lastIndex)
                } else if (element != ".") {
                    add(element)
                }
            }
        }
    }

    override fun isAbsolute(): Boolean = !this.rootId.isNullOrBlank()

    override fun getFileName(): DrivePath? = elements.lastOrNull()?.let { copy(rootId = null, elements = listOf(it)) }

    override fun getName(index: Int): DrivePath = if (index < 0 || index > elements.lastIndex) {
        throw IllegalArgumentException()
    } else {
        copy(rootId = null, elements = listOf(elements[index]))
    }

    override fun subpath(beginIndex: Int, endIndex: Int): DrivePath =
        copy(rootId = null, elements = (beginIndex..endIndex).map { elements[it] })

    private infix fun List<Any>.contentEquals(other: List<Any>): Boolean =
        this.withIndex().all { (index, any) -> other[index] == any }

    override fun endsWith(other: Path): Boolean = if (other is DrivePath) {
        val thisSize = elements.size
        val otherSize = other.elements.size
        (other.rootId == null
                && otherSize >= thisSize
                && elements.subList(thisSize - otherSize, thisSize) contentEquals other.elements)
                || (other.rootId != null && rootId != null && other.rootId == rootId && otherSize == thisSize && elements contentEquals other.elements)
    } else {
        false
    }

    override fun register(
        watcher: WatchService,
        events: Array<out WatchEvent.Kind<*>>,
        vararg modifiers: WatchEvent.Modifier
    ): WatchKey = if (watcher is DriveWatchService) {
        watcher.register(watcher, events, *modifiers)
    } else {
        throw ProviderMismatchException()
    }

    override fun relativize(other: Path): DrivePath = when {
        other !is DrivePath -> throw ProviderMismatchException()
        this == other -> copy(rootId = null, elements = emptyList())
        this.rootId == null && other.rootId == null
                && other.normalizedElements.size > normalizedElements.size
                && other.normalizedElements.subList(
            0,
            normalizedElements.size
        ) contentEquals normalizedElements -> copy(
            rootId = null,
            elements = other.normalizedElements.subList(normalizedElements.size, other.normalizedElements.size)
                .toList()
        )
        else -> throw IllegalArgumentException()
    }

    private fun getPathString(): String? = elements.takeIf { it.isNotEmpty() }?.joinToString(
        separator = "/",
        prefix = "/"
    ) { it.replace("/", "\\/") }

    override fun toUri(): URI = URI(
        "drive",
        null,
        "/${rootId ?: "root"}" + getPathString().orEmpty(),
        credentialId?.let { "credentialId=$it" },
        null
    )

    override fun toRealPath(vararg options: LinkOption): DrivePath = toAbsolutePath()

    override fun normalize(): DrivePath = copy(elements = normalizedElements)

    override fun getParent(): DrivePath? = when {
        !rootId.isNullOrBlank() && elements.isEmpty() -> null
        else -> copy(elements = (0 until elements.lastIndex).map { elements[it] })
    }

    private fun buildUriWithoutCredential(drivePath: DrivePath) = drivePath.toUri().let {
        URI(it.scheme, it.authority, it.path, null, null)
    }

    override fun compareTo(other: Path): Int = (other as DrivePath).let {
        buildUriWithoutCredential(this).compareTo(buildUriWithoutCredential(it))
    }

    override fun getNameCount(): Int = elements.size

    override fun startsWith(other: Path): Boolean =
        other is DrivePath && rootId == other.rootId && elements.size >= other.elements.size && elements.subList(
            0,
            other.elements.size
        ) contentEquals other.elements

    override fun getFileSystem(): DriveFileSystem = driveFileSystem

    override fun getRoot(): DrivePath? = rootId?.let { copy(elements = emptyList()) }

    override fun resolve(other: Path): DrivePath = (other as? DrivePath)?.let {
        when {
            !other.isAbsolute -> when {
                other.elements.isEmpty() -> this
                else -> other.copy(rootId = rootId, elements = elements + other.elements)
            }
            else -> other
        }
    } ?: throw ProviderMismatchException()

    override fun toAbsolutePath(): DrivePath = this.takeIf { rootId != null } ?: copy(rootId = "root")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrivePath) return false

        if (credentialId != other.credentialId) return false
        if (rootId != other.rootId) return false
        if (elements != other.elements) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId?.hashCode() ?: 0
        result = 31 * result + (rootId?.hashCode() ?: 0)
        result = 31 * result + elements.hashCode()
        return result
    }

    override fun toString(): String = toUri().toString()

    companion object {

        operator fun invoke(fileSystem: DriveFileSystem, uri: URI): DrivePath {
            val credentialId = Regex("credentialId=(.*)").find(uri.query.orEmpty())?.groupValues?.getOrNull(1)
            // We need a complex split and map to support escaped slashes in the path
            val path =
                uri.path.takeIf { it.isNotBlank() }?.split(Regex("(?<!\\\\)/")).orEmpty().map { it.replace("\\/", "/") }
            val rootId = path.getOrNull(1)?.takeIf { it.isNotBlank() }
            val elements = path.subList(2, path.size)
            return when {
                uri.scheme != "drive" -> throw ProviderMismatchException()
                else -> DrivePath(
                    fileSystem,
                    credentialId,
                    rootId,
                    elements
                )
            }
        }

        operator fun invoke(
            fileSystem: DriveFileSystem,
            credentialId: String? = null,
            rootId: String? = null,
            vararg elements: String
        ): DrivePath = DrivePath(
            fileSystem,
            credentialId,
            rootId,
            elements.toList()
        )
    }
}
