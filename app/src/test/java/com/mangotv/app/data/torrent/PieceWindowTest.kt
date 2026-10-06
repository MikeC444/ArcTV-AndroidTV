package com.mangotv.app.data.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceWindowTest {
    private val kb = 1024L
    // 100 pieces of 256 KiB (the last one short); the file starts 100 KiB into piece 10 and is 10 MiB long.
    private val layout = PieceLayout(256 * kb, 100 * 256 * kb - 50 * kb, 10 * 256 * kb + 100 * kb, 10 * 1024 * kb)

    @Test fun pieceMath() {
        assertEquals(100, layout.numPieces)
        assertEquals(10, layout.firstPiece)
        assertEquals(10, layout.pieceOf(0))
        assertEquals(10, layout.pieceOf(156 * kb - 1))
        assertEquals(11, layout.pieceOf(156 * kb))
        assertEquals(layout.lastPiece, layout.pieceOf(layout.fileSize - 1))
        assertEquals(layout.lastPiece, layout.pieceOf(layout.fileSize + 5000)) // clamped
        assertEquals(256 * kb, layout.pieceSize(0))
        assertEquals(206 * kb, layout.pieceSize(99))
    }

    @Test fun fileBytesInSharedEdgePieces() {
        assertEquals(156 * kb, layout.fileBytesIn(10))
        assertEquals(256 * kb, layout.fileBytesIn(11))
        assertEquals(layout.fileSize, (layout.firstPiece..layout.lastPiece).sumOf { layout.fileBytesIn(it) })
        assertEquals(0L, layout.fileBytesIn(layout.lastPiece + 1))
    }

    @Test fun headPiecesCoverTheRequestedBytes() {
        val head = layout.headPieces(512 * kb)
        assertEquals(10, head.first)
        assertTrue(head.sumOf { layout.fileBytesIn(it) } >= 512 * kb)
        assertEquals(layout.firstPiece..layout.firstPiece, layout.headPieces(1))
    }

    @Test fun windowSizeAndBounds() {
        val w = ReadAheadWindow(layout, 2 * 1024 * kb) // 8 pieces
        assertEquals(8, w.sizePieces)
        assertEquals(10..17, w.pieces)
        w.moveTo(layout.lastPiece - 2)
        assertEquals(layout.lastPiece - 2..layout.lastPiece, w.pieces) // never past the file
        w.moveTo(0)
        assertEquals(layout.firstPiece, w.start) // never before the file
    }

    @Test fun tinyReadAheadStillHasAUsableWindow() {
        assertEquals(ReadAheadWindow.MIN_PIECES, ReadAheadWindow(layout, 1).sizePieces)
    }

    @Test fun windowMovesOnSeeksAndAfterAQuarterOfAdvance() {
        val w = ReadAheadWindow(layout, 2 * 1024 * kb)
        assertFalse(w.shouldMoveTo(10))
        assertFalse(w.shouldMoveTo(11)) // quarter = 2 pieces
        assertTrue(w.shouldMoveTo(12))
        assertTrue(w.shouldMoveTo(40)) // seek forward
        w.moveTo(30)
        assertTrue(w.shouldMoveTo(12)) // seek back
    }

    @Test fun deadlinesAreOrderedAndCapped() {
        val w = ReadAheadWindow(layout, 100 * 256 * kb)
        val d = w.deadlines()
        assertEquals(ReadAheadWindow.MAX_DEADLINE_PIECES, d.size)
        assertEquals(10, d.first().first)
        assertTrue(d.zipWithNext().all { (a, b) -> b.first == a.first + 1 && b.second > a.second })
    }
}
