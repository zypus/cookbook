package com.zypus.cookbook

import kotlinx.html.dom.append
import kotlinx.html.js.li
import org.w3c.dom.*
import org.w3c.dom.events.KeyboardEvent
import org.w3c.xhr.XMLHttpRequest
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.hasClass
import kotlin.dom.removeClass

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-26
 */

object KQuery {

    fun String.findElement(): Element? {
        return if (this.startsWith("#")) {
            document.getElementById(this.drop(1))
        } else {
            throw Exception("$this is not start with a #")
        }
    }

    fun String.findHTMLElement(): HTMLElement? {
        return if (this.startsWith(".")) {
            document.getElementsByClassName(this.drop(1)).item(0) as? HTMLElement
        } else {
            this.findElement() as? HTMLElement
        }
    }

    fun String.findParagraph() = this.findHTMLElement() as? HTMLParagraphElement
    fun String.findDiv() = this.findHTMLElement() as? HTMLDivElement
    fun String.findTable() = this.findHTMLElement() as? HTMLTableElement
    fun String.findOrderedList() = this.findHTMLElement() as? HTMLOListElement
    fun String.findUnorderedList() = this.findHTMLElement() as? HTMLUListElement
    fun String.findImage() = this.findHTMLElement() as? HTMLImageElement
    fun String.findButton() = this.findHTMLElement() as? HTMLButtonElement

    fun String.findHTMLCollection(): HTMLCollection {
        return if (this.startsWith(".")) {
            document.getElementsByClassName(this.drop(1))
        } else {
            document.getElementsByTagName(this)
        }
    }


    fun String.findTableRows() = this.findHTMLCollection().asList() as List<HTMLTableRowElement>


}

fun kquery(block: KQuery.() -> Unit) {
    KQuery.block()
}

fun HTMLElement.hide() {
    this.addClass("hide")
}

fun HTMLElement.unhide() {
    this.removeClass("hide")
}

val HTMLElement.isHidden
    get() = hasClass("hide")

val HTMLElement.isNotHidden
    get() = !hasClass("hide")

fun fadeInSpinner() {
    js("""jQuery(".spinner").fadeIn()""")
}

fun main(args: Array<String>) {

    kquery {

        val spinner = ".spinner".findHTMLElement()

        val table = "#ingredients-table".findTable()
        val ingredientEditor = "#ingredients-editor".findDiv()
        val ingredientEditableList = "#ingredients-list".findUnorderedList()
        val ingredientError = "#ingredients-error".findParagraph()
        val ingredientEditButton = "#make-editable".findHTMLElement()
        val ingredientSaveButton = "#export-btn".findHTMLElement()
        val ingredientCancelButton = "#table-cancel".findHTMLElement()

        val list = "#steps".findOrderedList()
        val stepsContainer = "#step-container".findDiv()
        val stepsEditor = "#steps-editor".findDiv()
        val stepsEditableList = "#steps-list".findOrderedList()
        val stepsError = "#steps-error".findParagraph()
        val stepsEditButton = "#make-list-editable".findHTMLElement()
        val stepsSaveButton = "#list-save".findHTMLElement()
        val stepsCancelButton = "#list-cancel".findHTMLElement()

        ingredientEditButton?.onclick = {

            ingredientEditButton?.addClass("never-show")
            stepsEditButton?.addClass("never-show")

            table?.hide()
            val entries = table?.getElementsByClassName("ingredient")?.asList()?.filter {
                element ->
                element is HTMLElement && element.isNotHidden
            }?.map {
                element ->
                element.children.asList().filter { child ->
                    child is HTMLTableCellElement && child.hasClass("exportable")
                }.joinToString(separator = " ") {child ->
                    child.textContent!!
                }
            }

            ingredientEditor?.unhide()

            ingredientEditableList?.innerHTML = ""
            ingredientEditableList?.append {
                entries?.forEach {line ->
                    li {
                        +line
                    }
                }
            }

            ingredientEditableList?.onkeydown = {
                event ->
                if((event as KeyboardEvent).which == 8 && ingredientEditableList?.childElementCount == 1 && ingredientEditableList.textContent.isNullOrBlank()) {
                    event.preventDefault()
                }
            }

            ingredientEditableList?.focus()
        }

        ingredientCancelButton?.onclick = {
            ingredientEditor?.hide()
            table?.unhide()
            ingredientEditButton?.removeClass("never-show")
            stepsEditButton?.removeClass("never-show")
        }

        ingredientSaveButton?.onclick = {
            val content = ingredientEditableList?.getElementsByTagName("li")?.asList()?.joinToString(separator = "\n") { it.textContent!! }

            val request = XMLHttpRequest()
            request.open("POST", window.location.href + "/ingredients", true)
            request.setRequestHeader("Content-Type", "text/plain; charset=utf-8")

            request.onreadystatechange = {
                if (request.readyState == 4.toShort()) {
                    if (request.responseText.isBlank()) {
                        window.location.reload()
                    } else {
                        ingredientError?.textContent = request.responseText
                    }
                }
            }

            request.send(content)
        }


        stepsEditButton?.onclick = {

            ingredientEditButton?.addClass("never-show")
            stepsEditButton?.addClass("never-show")

            stepsContainer?.hide()
            val entries = list?.getElementsByClassName("step")?.asList()?.filter { element ->
                element is HTMLElement && element.isNotHidden && element.id != "template"
            }?.map { element ->
                element.children.asList().filter { child ->
                    child is HTMLParagraphElement && child.hasClass("exportable")
                }.joinToString(separator = " ") { child ->
                    child.textContent!!
                }
            }

            stepsEditor?.unhide()

            stepsEditableList?.innerHTML = ""
            stepsEditableList?.append {
                entries?.forEach { line ->
                    li {
                        +line
                    }
                }
            }

            stepsEditableList?.onkeydown = { event ->
                if ((event as KeyboardEvent).which == 8 && stepsEditableList?.childElementCount == 1 && stepsEditableList.textContent.isNullOrBlank()) {
                    event.preventDefault()
                }
            }

            stepsEditableList?.focus()
        }

        stepsCancelButton?.onclick = {
            stepsEditor?.hide()
            stepsContainer?.unhide()
            ingredientEditButton?.removeClass("never-show")
            stepsEditButton?.removeClass("never-show")
        }

        stepsSaveButton?.onclick = {
            val content = stepsEditableList?.getElementsByTagName("li")?.asList()
                ?.joinToString(separator = "\n") { it.textContent!! }

            val request = XMLHttpRequest()
            request.open("POST", window.location.href + "/instructions", true)
            request.setRequestHeader("Content-Type", "text/plain; charset=utf-8")

            request.onreadystatechange = {
                if (request.readyState == 4.toShort()) {
                    if (request.responseText.isBlank()) {
                        window.location.reload()
                    } else {
                        stepsError?.textContent = request.responseText
                    }
                }
            }

            request.send(content)


        }

        "#recipe-yield".findHTMLElement()?.apply {
            onclick = {
                val number = children[0] as HTMLElement
                val image = children[1] as HTMLElement
                val editField = children[2] as HTMLElement

                val previousContent = editField.textContent

                editField.onblur = {
                    number.unhide()
                    image.unhide()
                    editField.hide()

                    editField.textContent = previousContent
                    Unit
                }

                if (editField.isHidden) {
                    number.hide()
                    image.hide()
                    editField.unhide()
                    editField.focus()

                    editField.onkeydown = {event ->
                        val keyboardEvent = event as KeyboardEvent
                        if (keyboardEvent.which == 13) {
                            event.preventDefault()

                            fadeInSpinner()

                            val request = XMLHttpRequest()
                            request.open("POST", window.location.href + "/yield", true)
                            request.setRequestHeader("Content-Type", "text/plain; charset=utf-8")

                            request.onreadystatechange = {
                                if (request.readyState == 4.toShort()) {
                                    if (request.responseText.isBlank()) {
                                        window.location.reload()
                                    }
                                }
                            }

                            request.send(editField.textContent)

                            editField.blur()

                        }
                    }
                }

                Unit
            }
        }

        ".time".findHTMLCollection().asList().forEach { element ->
            val htmlElement = element as HTMLElement

            val span = htmlElement.getElementsByTagName("span")[0] as HTMLSpanElement

            span.onkeydown = { event ->
                val keyboardEvent = event as KeyboardEvent
                if (keyboardEvent.key !in "0".."9" && keyboardEvent.which !in listOf(8, 13, 37, 39)) {
                    println(keyboardEvent.which)
                    event.preventDefault()
                }
                if (keyboardEvent.which == 13) {
                    event.preventDefault()

                    fadeInSpinner()

                    val request = XMLHttpRequest()
                    request.open("POST", window.location.href + "/time/${span.id}", true)
                    request.setRequestHeader("Content-Type", "text/plain; charset=utf-8")

                    request.onreadystatechange = {
                        if (request.readyState == 4.toShort()) {
                            if (request.responseText.isBlank()) {
                                window.location.reload()
                            }
                        }
                    }

                    request.send(span.textContent)

                    span.blur()

                }
            }

            htmlElement.onclick = {
                span.addClass("editor-field")
                span.focus()
            }

            val previousContent = span.textContent

            span.onblur = {
                span.removeClass("editor-field")
                span.textContent = previousContent
                Unit
            }

        }

        "#editable-title".findHTMLElement()?.apply {
            var previousTitle = this.textContent

            onfocus = {
                previousTitle = this.textContent
                addClass("editor-field")
            }

            onblur = {
                this.textContent = previousTitle
                removeClass("editor-field")
            }

            onkeydown = {event ->
                val keyboardEvent = event as KeyboardEvent

                if (keyboardEvent.which == 13) {
                    event.preventDefault()

                    fadeInSpinner()

                    val request = XMLHttpRequest()
                    request.open("POST", window.location.href + "/title", true)
                    request.setRequestHeader("Content-Type", "text/plain; charset=utf-8")

                    request.onreadystatechange = {
                        if (request.readyState == 4.toShort()) {
                            if (request.status == 200.toShort()) {
                                window.open(request.responseURL, "_self")
                            } else {
                                window.alert(request.responseText)
                            }
                        }
                    }

                    request.send(this.textContent)

                }

            }
        }

        ".ingredient-icon".findHTMLCollection().asList().forEachIndexed {
            index, element ->
            val icon = element as HTMLElement

            if (icon.children.length > 0) {
                val image =
                    icon.getElementsByTagName("img").asList().first { !it.hasClass("icon-candidate") } as HTMLImageElement
                val candidates = icon.getElementsByTagName("div")

                if (candidates.length > 0) {
                    val candidateContainer = candidates[0] as HTMLElement

                    icon.onclick = {
                        candidateContainer.unhide()
                    }

                    candidateContainer.onmouseleave = {
                        candidateContainer.hide()
                    }

                    candidateContainer.getElementsByClassName("icon-candidate").asList().forEach {
                        val candidate = it as HTMLImageElement

                        candidate.onclick = {
                            val request = XMLHttpRequest()
                            request.open("POST", window.location.href + "/ingredients/$index", true)
                            request.setRequestHeader("Content-Type", "text/plain; charset=utf-8")

                            request.onreadystatechange = {
                                if (request.readyState == 4.toShort()) {
                                    if (request.responseText.isBlank()) {
                                        image.src = candidate.src
                                        candidateContainer.hide()
                                    } else {
                                        window.alert(request.responseText)
                                    }
                                }
                            }

                            request.send(candidate.alt)
                        }
                    }
                }
            }

        }

    }

}
