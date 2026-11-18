package com.delivery.smartnote.domain

interface SummarizationService { fun summarize(text: String): Summary }
data class Summary(val text: String, val points: List<String>, val method: String)

class ExtractiveSummarizer : SummarizationService {
    override fun summarize(text: String): Summary {
        val sentences = text.trim().split(Regex("(?<=[.!?])\\s+|\\n+")).asSequence().map { it.trim() }.filter { it.length > 12 }.toList()
        if (sentences.isEmpty()) return Summary("", emptyList(), "Local extractive summarizer")
        val words = Regex("[A-Za-z]{4,}").findAll(text.lowercase()).map { it.value }.filterNot { it in stopWords }.toList()
        val frequency = words.groupingBy { it }.eachCount()
        val chosen = sentences.asSequence().withIndex().sortedByDescending { (_, sentence) ->
            Regex("[A-Za-z]{4,}").findAll(sentence.lowercase()).sumOf { frequency[it.value] ?: 0 }.toDouble() / (sentence.length + 20)
        }.take(3).sortedBy { (index, _) -> index }.map { (_, value) -> value }.toList()
        return Summary(chosen.joinToString(" "), chosen, "Local extractive summarizer (not generative AI)")
    }
    private val stopWords = setOf("this", "that", "with", "from", "have", "were", "when", "which", "there", "their", "into", "about", "your", "they", "them", "then", "also", "than")
}

data class QuizQuestion(val prompt: String, val options: List<String>, val correctIndex: Int)
class LocalQuizGenerator {
    fun generate(text: String): List<QuizQuestion> {
        val pairs = text.lines().asSequence().mapNotNull { line ->
            val match = Regex("^\\s*([A-Za-z][A-Za-z0-9 ()/-]{2,45})\\s*[:–-]\\s*(.{12,})$").find(line)
            match?.let { it.groupValues[1].trim() to it.groupValues[2].trim() }
        }.distinctBy { (term, _) -> term.lowercase() }.toList()
        if (pairs.size < 4) return emptyList()
        return pairs.asSequence().take(8).mapIndexed { i, (term, definition) ->
            val distractors = pairs.indices.asSequence().filter { it != i }.map { pairs[it].second }.distinct().take(3).toList()
            val options = (distractors + definition).sortedBy { it.hashCode() xor i }
            QuizQuestion("What best describes $term?", options, options.indexOf(definition))
        }.toList()
    }
}

object Validation {
    fun validEmail(email: String) = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    fun validPassword(password: String) = password.length >= 8
}
