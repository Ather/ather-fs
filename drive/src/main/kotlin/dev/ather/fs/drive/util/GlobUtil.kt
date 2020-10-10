package dev.ather.fs.drive.util

internal object GlobUtil {

    // based on a ruby script for converting Globs to RegEx
    fun convertGlobToRegEx(line: String): Regex {
        var trimmedLine = line.trim()
        var length = line.length
        // Remove beginning and ending * globs because they're useless
        if (line.startsWith("*")) {
            trimmedLine = line.substring(1)
            length = line.length - 1
        }
        if (line.endsWith("*")) {
            trimmedLine = line.substring(0, length - 1)
        }
        var escaping = false
        var inCurlies = 0
        return buildString {
            for (currentChar in trimmedLine.toCharArray()) {
                when (currentChar) {
                    '*' -> {
                        if (escaping) append("\\*") else append(".*")
                        escaping = false
                    }
                    '?' -> {
                        if (escaping) append("\\?") else append('.')
                        escaping = false
                    }
                    '.', '(', ')', '+', '|', '^', '$', '@', '%' -> {
                        append('\\')
                        append(currentChar)
                        escaping = false
                    }
                    '\\' -> escaping = if (escaping) {
                        append("\\\\")
                        false
                    } else true
                    '{' -> {
                        if (escaping) {
                            append("\\{")
                        } else {
                            append('(')
                            inCurlies++
                        }
                        escaping = false
                    }
                    '}' -> {
                        if (inCurlies > 0 && !escaping) {
                            append(')')
                            inCurlies--
                        } else if (escaping) append("\\}") else append("}")
                        escaping = false
                    }
                    ',' -> if (inCurlies > 0 && !escaping) {
                        append('|')
                    } else if (escaping) append("\\,") else append(",")
                    else -> {
                        escaping = false
                        append(currentChar)
                    }
                }
            }
        }.toRegex()
    }
}
