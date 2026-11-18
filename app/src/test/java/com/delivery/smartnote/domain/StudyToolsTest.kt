package com.delivery.smartnote.domain

import org.junit.Assert.*
import org.junit.Test

class StudyToolsTest {
    @Test fun summaryUsesInput() {
        val input = "Photosynthesis converts light into chemical energy. Plants absorb sunlight using chlorophyll. Water and carbon dioxide are used to produce glucose."
        val (text, points) = ExtractiveSummarizer().summarize(input)
        assertTrue(text.contains("Photosynthesis") || text.contains("Plants"))
        assertFalse(points.isEmpty())
    }
    @Test fun quizNeedsFourDefinitions() {
        assertTrue(LocalQuizGenerator().generate("One: A single example only").isEmpty())
        val text = "CPU: Processes computer instructions\nRAM: Stores temporary working data\nSSD: Stores persistent digital files\nGPU: Renders graphics and images"
        assertEquals(4, LocalQuizGenerator().generate(text).size)
    }
    @Test fun passwordsRequireEightCharacters() { assertFalse(Validation.validPassword("123")); assertTrue(Validation.validPassword("12345678")) }
}
