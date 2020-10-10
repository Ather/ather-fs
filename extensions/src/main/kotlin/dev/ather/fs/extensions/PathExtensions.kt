package dev.ather.fs.extensions

import java.nio.file.Path

operator fun Path.plus(other: Path): Path = resolve(other)
operator fun Path.plus(other: String): Path = resolve(other)

/**
 * Example:
 * ```
 * "/a/b/c/d" / "/a/b" = "c/d"
 * ```
 *
 * Equivalent to calling:
 * ```
 * other.relativize(this)
 * ```
 *
 * @see Path.relativize
 */
operator fun Path.div(other: Path): Path = other.relativize(this)
