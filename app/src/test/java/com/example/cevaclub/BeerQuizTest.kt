package com.example.cevaclub

import com.example.cevaclub.data.BeerProfile
import com.example.cevaclub.data.BeerQuiz
import org.junit.Assert.assertEquals
import org.junit.Test

class BeerQuizTest {
    @Test fun separatesProfilesAtBothThresholds() {
        assertEquals(BeerProfile.CLASSICO, BeerQuiz.classify(listOf(1, 1, 1, 0, 0)))
        assertEquals(BeerProfile.EXPLORADOR, BeerQuiz.classify(listOf(1, 1, 1, 1, 0)))
        assertEquals(BeerProfile.EXPLORADOR, BeerQuiz.classify(listOf(2, 1, 1, 1, 1)))
        assertEquals(BeerProfile.ESPECIALISTA, BeerQuiz.classify(listOf(2, 2, 1, 1, 1)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIncompleteQuiz() {
        BeerQuiz.classify(listOf(0, 1, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownOptions() {
        BeerQuiz.classify(listOf(0, 1, 2, -1, 3))
    }
}
