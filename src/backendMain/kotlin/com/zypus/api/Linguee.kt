package com.zypus.api

import io.ktor.util.escapeHTML
import org.json.JSONArray
import org.json.JSONObject

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-27
 */
object Linguee {

    fun translate(source: String, sourceLang: Language, targetLang: Language): List<Translation> {

        val response = khttp.get(
            url = "https://linguee-api.herokuapp.com/api",
            params = mapOf(
                "q" to source.escapeHTML(),
                "src" to sourceLang.name.toLowerCase(),
                "dst" to targetLang.name.toLowerCase()
            )
        )

        if (response.statusCode == 200) {
            val jsonObject = response.jsonObject
            val exactMatches = jsonObject.optJSONArray("exact_matches") ?: JSONArray()
            return (0 until exactMatches.length()).map { index ->
                val match = exactMatches.getJSONObject(index)
                val translations = match.getJSONArray("translations")
                Translation(
                    term = asTerm(match),
                    translations = (0 until translations.length()).map { index2 ->
                        val translation = translations.getJSONObject(index2)
                        asTerm(translation)
                    }
                )
            }.sortedBy {
                if (it.term.text.toLowerCase() == source.toLowerCase()) {
                    0
                } else {
                    1
                }
            }
        } else {
            return emptyList()
        }

    }

}

    private fun asTerm(match: JSONObject): Term {
        return Term(
            text = match.getString("text"),
            info = match.getJSONObject("word_type").let {
                WordInfo(
                    type = it.optString("pos")?.let { pos -> WordType.fromApiReturn(pos) },
                    gender = it.optString("gender")?.let { gender ->
                        WordGender.fromApiReturn(gender)
                    }
                )
            }
        )
    }

    data class Translation(val term: Term, val translations: List<Term>)

data class Term(val text: String, val info: WordInfo)

data class WordInfo(val type: WordType? = null, val gender: WordGender? = null)

enum class WordType(val apiReturn: String) {
    NOUN("noun"),
    NOUN_AS_ADJECTIVE("noun as adjective"),
    VERB("verb"),
    ADJECTIVE("adjective"),
    ADJECTIVE_AS_PAST_PARTICIPLE("adjective / past participle"),
    ADVERB("adverb"),
    PRONOUN("pronoun"),
    PREPOSITION("preposition"),
    CONJUNCTION("conjunction"),
    INTERJECTION("interjection"),
    ARTICLE("article");

    companion object {
        fun fromApiReturn(apiReturn: String): WordType? {
            return WordType.values().firstOrNull {
                it.apiReturn == apiReturn
            }
        }
    }
}

enum class WordGender(val apiReturn: String) {
    NEUTER("neuter"),
    MASCULINE("masculine"),
    FEMININE("feminine");

    companion object {
        fun fromApiReturn(apiReturn: String): WordGender? {
            return WordGender.values().firstOrNull {
                it.apiReturn == apiReturn
            }
        }
    }
}

enum class Language(val lang: String) {
    BG("bulgarian"),
    CS("czech"),
    DA("danish"),
    DE("german"),
    EL("greek"),
    EN("english"),
    ES("spanish"),
    ET("estonian"),
    FI("finnish"),
    FR("french"),
    HU("hungarian"),
    IT("italian"),
    JA("japanese"),
    LT("lithuanian"),
    LV("latvian"),
    MT("maltese"),
    NL("dutch"),
    PL("polish"),
    PT("portuguese"),
    RO("romanian"),
    RU("russian"),
    SK("slovak"),
    SL("slovene"),
    SV("swedish"),
    ZH("chinese")
}

object LanguageContext {
    val BG = Language.BG
    val CS = Language.CS
    val DA = Language.DA
    val DE = Language.DE
    val EL = Language.EL
    val EN = Language.EN
    val ES = Language.ES
    val ET = Language.ET
    val FI = Language.FI
    val FR = Language.FR
    val HU = Language.HU
    val IT = Language.IT
    val JA = Language.JA
    val LT = Language.LT
    val LV = Language.LV
    val MT = Language.MT
    val NL = Language.NL
    val PL = Language.PL
    val PT = Language.PT
    val RO = Language.RO
    val RU = Language.RU
    val SK = Language.SK
    val SL = Language.SL
    val SV = Language.SV
    val ZH = Language.ZH
}

class Translator(val fromTo: Pair<Language, Language>) {

    fun translate(source: String): String {
        return ""
    }

}

fun String.translate(fromTo: LanguageContext.() -> Pair<Language, Language>): List<Translation> {
    val (sourceLang, targetLang) = LanguageContext.fromTo()
    return Linguee.translate(this, sourceLang, targetLang)
}

fun main(args: Array<String>) {
    Linguee.translate("orangeat", Language.DE, Language.EN)
}