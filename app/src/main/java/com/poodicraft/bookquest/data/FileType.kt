package com.poodicraft.bookquest.data

/**
 * Works out what a file actually is.
 *
 * The file name is the weakest signal of the three: pickers hand back names
 * with no extension at all, and a PDF called "chart" used to be filed as plain
 * text and rendered as the raw bytes of the document. So the leading bytes are
 * checked first, the MIME type the provider reports second, and the name last.
 */
object FileType {

    /** Enough bytes to recognise any of the signatures below. */
    const val HEAD_BYTES = 1024

    fun detect(displayName: String?, mimeType: String?, head: ByteArray): BookFormat {
        signatureOf(head)?.let { return it }

        when {
            mimeType == null -> Unit
            mimeType.startsWith("application/pdf") -> return BookFormat.PDF
            mimeType.startsWith("application/epub") -> return BookFormat.EPUB
            mimeType.startsWith("text/html") -> return BookFormat.HTML
            mimeType.startsWith("application/xhtml") -> return BookFormat.HTML
            mimeType.startsWith("text/") -> return BookFormat.TXT
        }

        val extension = displayName?.substringAfterLast('.', "").orEmpty()
        val byName = BookFormat.fromExtension(extension)
        if (byName != BookFormat.UNKNOWN) return byName

        // Nothing identified it, so fall back to whether it reads as text.
        return if (looksLikeText(head)) BookFormat.TXT else BookFormat.UNKNOWN
    }

    /** Recognises a format from the leading bytes alone. */
    private fun signatureOf(head: ByteArray): BookFormat? {
        if (startsWith(head, "%PDF")) return BookFormat.PDF

        // Zip container. An EPUB stores an uncompressed "mimetype" entry first,
        // so its media type sits in plain sight near the front of the file.
        if (head.size > 4 &&
            head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()
        ) {
            val window = String(head, 0, minOf(head.size, 200), Charsets.ISO_8859_1)
            return if (window.contains("epub")) BookFormat.EPUB else null
        }

        val text = String(head, 0, minOf(head.size, 400), Charsets.ISO_8859_1)
            .trimStart('﻿', ' ', '\n', '\r', '\t')
            .lowercase()
        if (text.startsWith("<!doctype html") || text.startsWith("<html")) return BookFormat.HTML

        return null
    }

    /**
     * True when the bytes plausibly read as writing rather than as a binary
     * document. A single NUL is decisive; beyond that it is the proportion of
     * control characters that gives a binary file away.
     */
    fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        var control = 0
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            if (value == 0) return false
            val isPlainWhitespace = value == 0x09 || value == 0x0A || value == 0x0D
            if (value < 0x20 && !isPlainWhitespace) control++
        }
        return control * 100 / bytes.size < 5
    }

    private fun startsWith(bytes: ByteArray, prefix: String): Boolean {
        if (bytes.size < prefix.length) return false
        for (index in prefix.indices) {
            if (bytes[index].toInt().toChar() != prefix[index]) return false
        }
        return true
    }
}
