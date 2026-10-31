package br.com.hermes.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoopDetectorTest {

    @Test
    fun `deve aprovar sequencia normal de acoes distintas sem loop`() {
        val detector = LoopDetector(windowSize = 8, repetitionThreshold = 3)

        assertEquals(LoopVerdict.Clear, detector.recordAction("search", "query=packet_login"))
        assertEquals(LoopVerdict.Clear, detector.recordAction("read_file", "path=Login.kt"))
        assertEquals(LoopVerdict.Clear, detector.recordAction("grep", "pattern=blowfish"))
        assertEquals(LoopVerdict.Clear, detector.recordAction("synthesize", "summary=found"))
    }

    @Test
    fun `deve detectar repeticao consecutiva identica apos threshold atingido`() {
        val detector = LoopDetector(windowSize = 8, repetitionThreshold = 3)

        assertEquals(LoopVerdict.Clear, detector.recordAction("search", "query=auth_token"))
        assertEquals(LoopVerdict.Clear, detector.recordAction("search", "query=auth_token"))

        val verdict = detector.recordAction("search", "query=auth_token")
        assertIs<LoopVerdict.Tripped>(verdict)
        assertEquals(LoopCause.CONSECUTIVE_IDENTICAL, verdict.cause)
        assertTrue(verdict.message.contains("Repetição consecutiva"))
    }

    @Test
    fun `deve detectar ciclo oscilatorio entre acoes A e B repetidas`() {
        val detector = LoopDetector(windowSize = 8, cycleLength = 2, cycleRepeatThreshold = 3)

        // Ciclo 1
        detector.recordAction("list_dir", "path=src")
        detector.recordAction("read_file", "path=src/Main.kt")
        // Ciclo 2
        detector.recordAction("list_dir", "path=src")
        detector.recordAction("read_file", "path=src/Main.kt")
        // Ciclo 3 -> deve acusar loop oscilatório
        detector.recordAction("list_dir", "path=src")
        val verdict = detector.recordAction("read_file", "path=src/Main.kt")

        assertIs<LoopVerdict.Tripped>(verdict)
        assertEquals(LoopCause.OSCILLATING_CYCLE, verdict.cause)
        assertTrue(verdict.message.contains("Ciclo oscilatório"))
    }

    @Test
    fun `deve resetar o detector permitindo nova analise limpa`() {
        val detector = LoopDetector(windowSize = 8, repetitionThreshold = 3)
        detector.recordAction("search", "query=abc")
        detector.recordAction("search", "query=abc")

        detector.reset()

        assertEquals(LoopVerdict.Clear, detector.recordAction("search", "query=abc"))
    }
}
