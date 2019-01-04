package com.zypus

import io.ktor.html.Template
import io.ktor.html.TemplatePlaceholder
import kotlinx.html.*
import java.net.URI
import java.util.*

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-08
 */

fun FlowOrInteractiveOrPhrasingContent.icon8(term: String,
                                             classes: String? = "icon8",
                                             set: String = "iOS-glyphs",
                                             size: Int? = 100,
                                             color: String? = null,
                                             block: IMG.() -> Unit = {}) {
    val escapedTerm = term.replace("[^a-zA-Z]+".toRegex(), "_")
    img(alt = escapedTerm, src = icon8Url(
        escapedTerm,
        set,
        size = size,
        color = color
    ), classes = classes, block = block)
}

fun FlowOrInteractiveOrPhrasingContent.placeholderImg(term: String,
                                                      classes: String? = null,
                                                      block: IMG.() -> Unit = {}) {
    img(alt = term, src = ImageManager.getImageUrlFor(term), classes = classes, block = block)
}

fun jdbcConnectInfo(): Triple<String, String, String> {
    val dbUri = URI(System.getenv("DATABASE_URL"))

    val username = dbUri.userInfo?.split(":")?.get(0) ?: ""
    val password = dbUri.userInfo?.split(":")?.getOrNull(1) ?: ""
    val dbUrl = "jdbc:postgresql://${dbUri.host}${dbUri.port.takeIf { it > 0 }?.let { ":$it" } ?: ""}${dbUri.path}${if(password != "")"?sslmode=require" else ""}"
    return Triple(dbUrl, username, password)
}

fun icon8Url(
    term: String,
    set: String = "iOS",
    size: Int? = 100,
    color: String? = null): String {
    return checkedIcon8Url(term, set, size, color, check = false)!!
}

fun checkedIcon8Url(
    term: String,
    set: String = "iOS",
    size: Int? = 100,
    color: String? = null,
    check: Boolean = true): String? {

    val url = "https://png.icons8.com/$set/${term.replace("[^a-zA-Z]+".toRegex(), "_")}" + (size?.let { "/$it" }
            ?: "") + (color?.let { "/$it" }
            ?: "")

    return if (check) {
        url.takeIf { khttp.get(it).statusCode == 200 }
    } else {
        url
    }

}

fun FlowOrPhrasingContent.highlighted(text: String, highlightLookup: Map<String, String>) {
    val split = text.split(" ")
    var lastColorWord = ""
    var previousSpan = ""
    split.forEachIndexed { index, word ->
        val colorWord = highlightLookup.keys.find {
            val sanitise = word.sanitise()
            if (sanitise.length > 2) {
                it.toLowerCase().contains(sanitise)
            } else {
                false
            }
        }
        if (colorWord != null) {
            if (previousSpan.isNotBlank() && colorWord != lastColorWord) {
                span(highlightLookup[lastColorWord] + " highlighted") {
                    +previousSpan.trim()
                }
                previousSpan = ""
            }
            previousSpan += " " + word.capitalize()
            lastColorWord = colorWord
        } else {
            if (previousSpan.isNotBlank()) {
                span(highlightLookup[lastColorWord] + " highlighted") {
                    +previousSpan.trim()
                }
                +" "
                previousSpan = ""
                lastColorWord = ""
            }
            +word
        }
        if (index < split.size - 1) {
            +" "
        }
    }

    if (previousSpan.isNotBlank()) {
        span(highlightLookup[lastColorWord] + " highlighted") {
            +previousSpan.trim()
        }
    }
}

fun String.words(): List<String> {
    return this.split("[^A-Za-zäöüÄÖÜß-]".toRegex())
}

fun String.firstWord(): String {
    return this.words().first()
}

fun String.lastWord(): String {
    return this.words().last()
}

fun String.sanitise(): String {
    return this.toLowerCase().replace("[^a-zäöüß]".toRegex(), "")
}

open class TemplatePlaceholderList<TTemplate : Template<TOuter>, TOuter>() {
    private var items = ArrayList<TemplatePlaceholderItem<TTemplate, TOuter>>()
    operator fun invoke(content: TTemplate.() -> Unit = {}) {
        val placeholder = TemplatePlaceholderItem(items.size, items)
        placeholder(content)
        items.add(placeholder)
    }

    fun isEmpty(): Boolean = items.size == 0
    fun apply(destination: TOuter, render: TOuter.(TemplatePlaceholderItem<TTemplate, TOuter>) -> Unit) {
        for (item in items) {
            destination.render(item)
        }
    }
}

class TemplatePlaceholderItem<TTemplate : Template<TOuter>, TOuter>(val index: Int, val collection: List<TemplatePlaceholderItem<TTemplate, TOuter>>) : TemplatePlaceholder<TTemplate>() {
    val first: Boolean get() = index == 0
    val last: Boolean get() = index == collection.lastIndex
}

/**
 * Inserts every element of placeholder list
 */
fun <TTemplate: Template<TOuter>, TOuter> TOuter.each(
    items: TemplatePlaceholderList<TTemplate, TOuter>,
    itemTemplate: TOuter.(TemplatePlaceholderItem<TTemplate, TOuter>) -> Unit
): Unit {
    items.apply(this, itemTemplate)
}
