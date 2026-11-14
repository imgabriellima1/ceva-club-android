package com.example.cevaclub.data

import java.text.NumberFormat
import java.util.Locale

internal enum class Plan(
    val code: String,
    val title: String,
    val cents: Int,
    val benefits: String,
) {
    ESSENCIAL("essential", "Essencial", 5990, "4 cervejas leves e um guia de degustação."),
    DESCOBERTA("discovery", "Descoberta", 8990, "6 cervejas de estilos variados e sugestões de harmonização."),
    RESERVA("reserve", "Reserva", 12990, "8 cervejas, incluindo rótulos sazonais, e fichas de degustação.");

    fun price() = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(cents / 100.0)

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

internal enum class BeerProfile(
    val code: String,
    val title: String,
    val description: String,
    val suggestedPlan: Plan,
) {
    CLASSICO("classic", "Clássico", "Você prefere cervejas leves, refrescantes e fáceis de harmonizar.", Plan.ESSENCIAL),
    EXPLORADOR("explorer", "Explorador", "Você gosta de variar os estilos e descobrir sabores e aromas novos.", Plan.DESCOBERTA),
    ESPECIALISTA("specialist", "Especialista", "Você busca cervejas intensas, processos diferentes e rótulos com personalidade.", Plan.RESERVA);

    companion object {
        fun fromCode(code: String?) = entries.firstOrNull { it.code == code }
    }
}

internal object BeerQuiz {
    data class Question(val title: String, val options: List<String>)

    val questions = listOf(
        Question("Que sabor você costuma escolher?", listOf("Leve e refrescante", "Frutado ou aromático", "Intenso e complexo")),
        Question("Qual estilo desperta mais sua curiosidade?", listOf("Pilsen ou Lager", "Weiss ou Pale Ale", "Imperial Stout ou Barleywine")),
        Question("Qual nível de amargor você prefere?", listOf("Baixo", "Moderado", "Alto")),
        Question("Com que frequência você experimenta estilos novos?", listOf("Raramente", "De vez em quando", "Sempre que encontro algo diferente")),
        Question("O que você procuraria em uma caixa?", listOf("Rótulos fáceis de beber", "Estilos variados para descobrir", "Seleções sazonais e intensas")),
    )

    fun classify(answers: List<Int>): BeerProfile {
        require(answers.size == questions.size)
        require(answers.withIndex().all { (index, answer) -> answer in questions[index].options.indices })
        return when (answers.sum()) {
            in 0..3 -> BeerProfile.CLASSICO
            in 4..6 -> BeerProfile.EXPLORADOR
            else -> BeerProfile.ESPECIALISTA
        }
    }
}
