package com.franklinharper.battlezone

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RecordingTest {
    @Test
    fun exportImportAndUndoRedoStayConsistent() {
        val map = MapGenerator.generate(seed = 123L, playerCount = 2)
        val bots: Array<Bot> = Array(2) { DefaultBot(GameRandom(42L)) }
        val controller = GameController(
            initialMap = map,
            gameMode = GameMode.BOT_VS_BOT,
            humanPlayerId = 0,
            bots = bots
        )

        val initialPlayer = controller.gameState.value.currentPlayerIndex
        val initialRecording = RecordingSerializer.decode(controller.exportRecordingJson())
        assertEquals(2, initialRecording.version)
        assertEquals(0, initialRecording.events.size)
        assertTrue(initialRecording.initialSnapshot != null)

        controller.skipTurn()
        val afterSkipPlayer = controller.gameState.value.currentPlayerIndex
        assertNotEquals(initialPlayer, afterSkipPlayer)

        val afterSkipRecording = RecordingSerializer.decode(controller.exportRecordingJson())
        assertEquals(1, afterSkipRecording.events.size)

        assertTrue(controller.canUndo())
        controller.undo()
        assertEquals(initialPlayer, controller.gameState.value.currentPlayerIndex)

        assertTrue(controller.canRedo())
        controller.redo()
        assertEquals(afterSkipPlayer, controller.gameState.value.currentPlayerIndex)

        val recordingJson = controller.exportRecordingJson()
        val compressed = RecordingCompression.compressToBytes(recordingJson)
        val decompressed = RecordingCompression.decompressToJson(compressed)
        assertEquals(recordingJson, decompressed)
        val replayController = GameController(
            initialMap = map.deepCopy(),
            gameMode = GameMode.BOT_VS_BOT,
            humanPlayerId = 0,
            bots = Array<Bot>(2) { DefaultBot(GameRandom(99L)) }
        )
        assertTrue(replayController.importRecordingJson(recordingJson))
        assertEquals(initialPlayer, replayController.gameState.value.currentPlayerIndex)

        replayController.redo()
        assertEquals(afterSkipPlayer, replayController.gameState.value.currentPlayerIndex)
    }
}
