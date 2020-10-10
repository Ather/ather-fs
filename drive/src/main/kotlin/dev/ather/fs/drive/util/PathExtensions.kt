package dev.ather.fs.drive.util

import dev.ather.fs.drive.DrivePath
import java.nio.file.Path
import java.nio.file.ProviderMismatchException

internal inline val Path.drivePath: DrivePath get() = (this as? DrivePath) ?: throw ProviderMismatchException()
