package dev.ather.fs.drive

import java.nio.file.FileStore
import java.nio.file.attribute.FileAttributeView
import java.nio.file.attribute.FileStoreAttributeView

sealed class DriveFileStore : FileStore() {

    abstract val name: String
    abstract val rootId: String

    object MyDrive : DriveFileStore() {
        override val name: String = "My Drive"
        override val rootId: String = "root"

        override fun getTotalSpace(): Long = Long.MAX_VALUE // TODO Call drive/v3/about (limit property)

        override fun getUsableSpace(): Long = Long.MAX_VALUE // TODO Call drive/v3/about (usageInDrive property)
    }

    data class SharedDrive(
        override val name: String,
        override val rootId: String
    ) : DriveFileStore() {

        override fun getTotalSpace(): Long = Long.MAX_VALUE
        override fun getUsableSpace(): Long = Long.MAX_VALUE // TODO Subtract usage from Long.MAX_VALUE
    }

    override fun supportsFileAttributeView(type: Class<out FileAttributeView>): Boolean = when (type) {
        DriveFileStoreAttributeView::class.java -> true
        else -> false
    }

    override fun supportsFileAttributeView(name: String): Boolean = when (name) {
        "drive" -> true
        else -> false
    }

    override fun name(): String = name

    override fun isReadOnly(): Boolean = isReadOnly

    @Suppress("UNCHECKED_CAST") // we can do the unchecked casts here because of the class type checks
    override fun <V : FileStoreAttributeView> getFileStoreAttributeView(type: Class<V>): V? = when (type) {
        DriveFileStoreAttributeView::class.java -> when (this) {
            MyDrive -> DriveFileStoreAttributeView.YourDriveFileStoreAttributeView() as V
            is SharedDrive -> DriveFileStoreAttributeView.SharedDriveFileStoreAttributeView(name) as V
        }
        else -> null
    }

    override fun getAttribute(attribute: String): Any? = when (attribute) {
        "drive:isReadOnly" -> false // TODO use OAuth scope to determine this
        "drive:name" -> name
        else -> throw UnsupportedOperationException("Attribute \"$attribute\" is not supported")
    }

    override fun type(): String = when (this) {
        MyDrive -> "user"
        is SharedDrive -> "shared"
    }

    override fun getUnallocatedSpace(): Long = 0L
}
