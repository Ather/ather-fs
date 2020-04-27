package dev.ather.fs.drive

import java.net.URI
import java.nio.file.*

sealed class DrivePath : Path {

    protected abstract val driveFileSystem: DriveFileSystem
    protected abstract val accountId: String?

    internal data class FileId(
        override val driveFileSystem: DriveFileSystem,
        override val accountId: String?,
        val fileId: String
    ) : DrivePath() {
        override fun toString() = super.toString()
    }

    internal data class Named(
        override val driveFileSystem: DriveFileSystem,
        override val accountId: String?,
        val rootId: String? = "root",
        val elements: List<String>
    ) : DrivePath() {

        val normalizedElements: List<String> by lazy {
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

        override fun toString() = super.toString()
    }

    override fun isAbsolute(): Boolean = this is Named && !this.rootId.isNullOrBlank() || this is FileId

    override fun getFileName(): DrivePath? = when (this) {
        is FileId -> null
        is Named -> elements.lastOrNull()?.let { copy(elements = listOf(it)) }
    }

    override fun getName(index: Int): DrivePath = when (this) {
        is FileId -> throw IllegalArgumentException()
        is Named -> if (index < 0 || index > elements.lastIndex) {
            throw IllegalArgumentException()
        } else {
            copy(rootId = null, elements = listOf(elements[index]))
        }
    }

    override fun subpath(beginIndex: Int, endIndex: Int): DrivePath = when (this) {
        is FileId -> throw IllegalArgumentException()
        is Named -> copy(rootId = null, elements = (beginIndex..endIndex).map { elements[it] })
    }

    private infix fun List<Any>.contentEquals(other: List<Any>): Boolean =
        this.withIndex().all { (index, any) -> other[index] == any }

    override fun endsWith(other: Path): Boolean = when (this) {
        is FileId -> this == other
        is Named -> if (other is Named) {
            val thisSize = elements.size
            val otherSize = other.elements.size
            (other.root == null
                    && otherSize >= thisSize
                    && elements.subList(thisSize - otherSize, thisSize) contentEquals other.elements)
                    || (other.root != null && root != null && other.root == root && otherSize == thisSize && elements contentEquals other.elements)
        } else {
            false
        }
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

    override fun relativize(other: Path): DrivePath = when (this) {
        is FileId -> throw IllegalArgumentException()
        is Named -> when {
            other !is DrivePath -> throw ProviderMismatchException()
            other !is Named -> throw IllegalArgumentException()
            this == other -> copy(rootId = null, elements = emptyList())
            this.root == null && other.root == null
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
    }

    override fun toUri(): URI = URI(
        "drive",
        when (this) {
            is FileId -> fileId
            is Named -> rootId ?: "root"
        },
        when (this) {
            is FileId -> null
            is Named -> elements.joinToString(
                separator = "/",
                prefix = "/"
            ) { it.replace("/", "\\/") }
        },
        accountId?.let { "accountId=$it" },
        null
    )

    override fun toRealPath(vararg options: LinkOption): DrivePath = toAbsolutePath()

    override fun normalize(): DrivePath = when (this) {
        is FileId -> this
        is Named -> copy(elements = normalizedElements)
    }

    override fun getParent(): DrivePath? = when (this) {
        is FileId -> null
        is Named -> copy(elements = (0 until elements.lastIndex).map { elements[it] })
    }

    private fun buildUriWithoutCredential(drivePath: DrivePath) = drivePath.toUri().let {
        URI(it.scheme, it.authority, it.path, null, null)
    }

    override fun compareTo(other: Path): Int = (other as DrivePath).let {
        buildUriWithoutCredential(this).compareTo(buildUriWithoutCredential(it))
    }

    override fun getNameCount(): Int = when (this) {
        is FileId -> 0
        is Named -> elements.size
    }

    override fun startsWith(other: Path): Boolean = when (this) {
        is FileId -> other is FileId && fileId == other.fileId
        is Named -> other is Named && rootId == other.rootId && elements.size >= other.elements.size && elements.subList(
            0,
            other.elements.size
        ) contentEquals other.elements
    }

    override fun getFileSystem(): DriveFileSystem = driveFileSystem

    override fun getRoot(): DrivePath? = when (this) {
        is FileId -> this
        is Named -> rootId?.let { copy(elements = emptyList()) }
    }

    override fun resolve(other: Path): DrivePath = (other as? DrivePath)?.let {
        when (this) {
            is FileId -> when {
                other is Named && !other.isAbsolute -> other.copy(rootId = fileId)
                else -> other
            }
            is Named -> when {
                other is Named && !other.isAbsolute -> when {
                    other.elements.isEmpty() -> this
                    else -> other.copy(rootId = rootId, elements = elements + other.elements)
                }
                else -> other
            }
        }
    } ?: throw ProviderMismatchException()

    override fun toAbsolutePath(): DrivePath = when (this) {
        is FileId -> this
        is Named -> this.takeIf { rootId != null } ?: copy(rootId = "root")
    }

    override fun toString(): String = toUri().toString()

    companion object {

        operator fun invoke(fileSystem: DriveFileSystem, uri: URI): DrivePath {
            val accountId = Regex("accountId=(.*)").find(uri.query.orEmpty())?.groupValues?.getOrNull(1)
            // We need a complex split and map to support escaped slashes in the path
            val elements =
                uri.path.takeIf { it.isNotBlank() }?.split(Regex("(?<!\\\\)/")).orEmpty().map { it.replace("\\/", "/") }
            return when {
                uri.scheme != "drive" -> throw ProviderMismatchException()
                elements.isNotEmpty() -> Named(
                    fileSystem,
                    accountId,
                    uri.authority?.takeIf { it.isNotBlank() },
                    elements.subList(1, elements.size)
                )
                else -> FileId(
                    fileSystem,
                    accountId,
                    uri.authority ?: "root"
                )
            }
        }

        operator fun invoke(
            fileSystem: DriveFileSystem,
            accountId: String? = null,
            rootId: String? = null,
            vararg elements: String
        ): DrivePath = when {
            elements.isEmpty() && !rootId.isNullOrBlank() -> FileId(fileSystem, accountId, rootId)
            else -> Named(
                fileSystem,
                accountId,
                rootId,
                elements.toList()
            )
        }
    }
}
