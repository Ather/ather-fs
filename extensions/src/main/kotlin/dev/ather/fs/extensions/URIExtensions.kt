package dev.ather.fs.extensions

import java.net.URI
import java.nio.file.Path

fun URI.toPath() = Path.of(this)
