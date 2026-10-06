package com.mangotv.app.data.torrent

/**
 * Where one file sits inside a torrent's piece grid. Torrent data is one long byte stream cut into equal [pieceLength] pieces, and a file
 * is a slice of it starting at [fileOffset], so a byte of the file lives in piece `(fileOffset + byte) / pieceLength`.
 */
class PieceLayout(
    val pieceLength: Long,
    val totalSize: Long,
    val fileOffset: Long,
    val fileSize: Long
) {
    init {
        require(pieceLength > 0 && fileSize > 0 && fileOffset >= 0 && fileOffset + fileSize <= totalSize) { "file does not fit the torrent" }
    }

    val numPieces: Int = ((totalSize + pieceLength - 1) / pieceLength).toInt()
    val firstPiece: Int = (fileOffset / pieceLength).toInt()
    val lastPiece: Int = ((fileOffset + fileSize - 1) / pieceLength).toInt()

    fun pieceOf(fileByte: Long): Int = ((fileOffset + fileByte.coerceIn(0, fileSize - 1)) / pieceLength).toInt()

    fun pieceSize(piece: Int): Long =
        if (piece == numPieces - 1) totalSize - piece.toLong() * pieceLength else pieceLength

    /** The pieces holding file bytes [start]..[endInclusive]. */
    fun piecesFor(start: Long, endInclusive: Long): IntRange = pieceOf(start)..pieceOf(endInclusive)

    /** Bytes of the file that a piece holds (the first and last piece are shared with neighbouring files). */
    fun fileBytesIn(piece: Int): Long {
        val pieceStart = piece.toLong() * pieceLength
        val from = maxOf(pieceStart, fileOffset)
        val to = minOf(pieceStart + pieceSize(piece), fileOffset + fileSize)
        return (to - from).coerceAtLeast(0)
    }

    /** Pieces covering the first [bytes] of the file (the start buffer). */
    fun headPieces(bytes: Long): IntRange = firstPiece..pieceOf((bytes.coerceIn(1, fileSize)) - 1)
}

/**
 * The stretch of pieces being fetched ahead of the picture. It starts at the piece being read and is [sizePieces] long; it only moves when
 * the reader has gone [moveAfterPieces] pieces on (so the engine is not re-prioritising on every read) or has jumped (a seek), in either
 * direction. Everything outside it is left undownloaded, which is what keeps storage and bandwidth bounded.
 */
class ReadAheadWindow(private val layout: PieceLayout, readAheadBytes: Long) {
    val sizePieces: Int = (readAheadBytes / layout.pieceLength).toInt().coerceAtLeast(MIN_PIECES)
    private val moveAfterPieces: Int = (sizePieces / 4).coerceAtLeast(1)

    var start: Int = layout.firstPiece
        private set

    val pieces: IntRange get() = start..minOf(start + sizePieces - 1, layout.lastPiece)

    /** True when a read at [piece] is far enough from where the window starts that the window should be re-aimed at it. */
    fun shouldMoveTo(piece: Int): Boolean = piece < start || piece >= start + moveAfterPieces

    fun moveTo(piece: Int) {
        start = piece.coerceIn(layout.firstPiece, layout.lastPiece)
    }

    /**
     * Pieces that must arrive soon, in order, with how many milliseconds from now each is wanted: the nearest [maxCount] of the window,
     * [stepMs] apart, so the swarm hands over the piece the player needs next before the ones after it.
     */
    fun deadlines(maxCount: Int = MAX_DEADLINE_PIECES, stepMs: Int = DEADLINE_STEP_MS): List<Pair<Int, Int>> =
        pieces.take(maxCount).mapIndexed { i, piece -> piece to (i * stepMs + DEADLINE_BASE_MS) }

    companion object {
        const val MIN_PIECES = 4
        const val MAX_DEADLINE_PIECES = 24
        const val DEADLINE_STEP_MS = 250
        const val DEADLINE_BASE_MS = 100
    }
}
