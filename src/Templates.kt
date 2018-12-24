package com.zypus

import io.ktor.html.*
import kotlinx.css.Image
import kotlinx.css.LinearDimension
import kotlinx.css.TextAlign
import kotlinx.html.*

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-08
 */


class CookbookTemplate(val main: MainTemplate = MainTemplate()) : Template<HTML> {
    var bannerImageUrl: String? = null
    var categoriesUrl = ""
    val category = PlaceholderList<DIV, FlowContent>()
    override fun HTML.apply() {
        insert(main) {
            menu {
                item {
                    a(href = "/", classes = "active") {
                        icon8("cookbook", set = "ios-glyphs", color=StyleGuide.highlightColor)
                    }
                }
            }
            content {
                section {
                    id = "banner"
                    if (bannerImageUrl != null) {
                        style {
                            backgroundImage = Image("url($bannerImageUrl)")
                        }
                    }
                    div("inner") {
                        h1 { +"Cookbook" }
                        p { +"Das Kochbuch der Familie Fränz" }
                    }
                }
                section {
                    id = "galleries"
                    div("gallery") {
                        header("special") {
                            h2 {
                                +"Ausgewählte Kategorien"
                            }
                        }
                        div("content") {
                            if (!category.isEmpty()) {
                                each(category) {
                                    div("media") {
                                        insert(it)
                                    }
                                }
                            }
                        }
                        footer {
                            a(href = categoriesUrl, classes = "button big") {
                                +"Alle Kategorien"
                            }
                        }
                    }
                }

            }
        }
    }
}

class GalleryTemplate(val main: MainTemplate = MainTemplate()) : Template<HTML> {
    var galleryName = ""
    var isNew = false
    var isEditable = false
    var menuList: List<Pair<String, String>> = arrayListOf()
    var tags: List<String> = arrayListOf()
    val footer = Placeholder<FOOTER>()
    val galleryItem = PlaceholderList<DIV, FlowContent>()
    val galleryItemTags: List<List<String>> = arrayListOf()
    override fun HTML.apply() {
        insert(main) {
            menu {
                item { a(href = "/") {
                    icon8("cookbook", set = "ios-glyphs", color = "FFFFFF")
                } }
                menuList.forEachIndexed {
                    index, entry ->
                    item {
                        a(href = entry.second, classes = if (index == menuList.size-1) "active" else null) {
                            icon8(entry.first, set = "ios-glyphs", color = if (index == menuList.size - 1) StyleGuide.highlightColor else "FFFFFF" )
                        }
                    }
                }

            }
            content {
                section {
                    id = "galleries"
                    header {
                        id = "header"
                        div {
                            +"Kategorien"
                        }
                    }
                    div("gallery") {
                        header {
                            h1 {
                                if(isEditable) {
                                    id = "editable-title"
                                    contentEditable = true
                                    if (isNew) {
                                        attributes["autofocus"] = ""
                                    }
                                }
                                +galleryName.capitalize()

                            }
                            if (tags.isNotEmpty()) {
                                ul {
                                    li {
                                        a(href="#", classes = "button active") {
                                            attributes["data-tag"] = "all"
                                            +"Alle"
                                        }
                                    }
                                    tags.forEach {
                                        tag ->
                                        li {
                                            a(href = "#", classes = "button") {
                                                attributes["data-tag"] = tag
                                                +tag.capitalize()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        div("content") {
                            if (!galleryItem.isEmpty()) {
                                if (!galleryItem.isEmpty()) {
                                    var tagIndex = 0
                                    each(galleryItem) {
                                        val rTags = galleryItemTags.getOrNull(tagIndex)
                                        div("media" + if (rTags.isNullOrEmpty()) "" else rTags.joinToString(" ", " ")) {
                                            insert(it)
                                        }
                                        ++tagIndex
                                    }
                                }
                            }
                        }
                        if (!isNew) {
                            footer {
                                insert(footer)
                            }
                        }
                    }
                }
            }
        }
    }
}

class StepTemplate() : Template<OL> {
    val stepImage = Placeholder<DIV>()
    val stepInstruction = Placeholder<P>()
    override fun OL.apply() {
        li("step") {
            div("step-image") {
                insert(stepImage)
            }
            p {
                insert(stepInstruction)
            }
//            div("stepInstruction") {
//                p {
//                    insert(stepInstruction)
//                }
//            }
        }
    }
}

class IngredientTemplate() : Template<TABLE> {
    val ingredientImage = Placeholder<TD>()
    val ingredientName = Placeholder<TD>()
    val amountValue = Placeholder<TD>()
    val amountUnit = Placeholder<TD>()
    override fun TABLE.apply() {
        tr("ingredient") {
            td("ingredient-amount exportable editable") {
                insert(amountValue)
            }
            td("exportable editable") {
                insert(amountUnit)
            }
            td("ingredient-icon") {
                insert(ingredientImage)
            }
            td("ingredient-name exportable editable") {
                insert(ingredientName)
            }
            td(classes = "controls hide") {
                img(src = icon8Url("circled_chevron_down", set = "ios-glyphs"), classes = "table-down control")
                img(src = icon8Url("slide_up", set = "ios-glyphs"), classes = "table-up control")
                img(
                    src = icon8Url("insert_row", set = "ios-glyphs"),
                    classes = "table-add control"
                )
                img(src = icon8Url("delete_row", set = "ios-glyphs"), classes = "table-remove control")
            }
        }
    }
}

class RecipeTemplate(val main: MainTemplate = MainTemplate()) : Template<HTML> {
    var menuList: List<Pair<String, String>> = arrayListOf()
    var recipeName = "Leckeres Rezept ohne Namen"
    var imageUploadUrl = ""
    var recipeImageUrl = ""
    var isNew = false
    val category = Placeholder<FlowContent>()
    val recipe = Placeholder<FlowContent>()
    val ingredient = TemplatePlaceholderList<IngredientTemplate, TABLE>()
    val step = TemplatePlaceholderList<StepTemplate, OL>()
    override fun HTML.apply() {
        insert(main) {
            menu {
                item {
                    a(href = "/") {
                        icon8("cookbook", set = "ios-glyphs", color = "FFFFFF", size = 80)
                    }
                }
                menuList.forEachIndexed { index, entry ->
                    item {
                        a(href = entry.second, classes = if (index == menuList.size - 1) "active" else null) {
                            icon8(
                                entry.first,
                                set = "ios-glyphs",
                                color = if (index == menuList.size - 1) StyleGuide.highlightColor else "FFFFFF"
                            )
                        }
                    }
                }
            }
            content {
                section {
                    style {
                        backgroundImage = Image("url($recipeImageUrl)")
                    }
                    id = "banner"
                    if (!isNew) {
                        form(
                            encType = FormEncType.multipartFormData,
                            action = imageUploadUrl,
                            method = FormMethod.post
                        ) {
                            id = "image-upload-form"
                            label {
                                htmlFor = "image-upload"
                                img(src = icon8Url("edit", "ios-glyphs", color = "FFFFFF"), classes = "control hide") {
                                    id = "change-banner"
                                }
                            }
                            fileInput(name = "file", classes = "hide") {
                                id = "image-upload"
                                accept = "image/*"
                                onChange = "function () { document.getElementById('image-upload-form').submit(); };"
                            }
                        }
                    }
                    div("inner") {
                        h1 {
                            id = "editable-title"
                            contentEditable = true
                            if (isNew) {
                                attributes["autofocus"] = ""
                            }
                            +recipeName
                        }
//                        p { +"The shared cookbook of the Fränz family" }
                    }
                }
                section {
                    id = "recipe"
                    div("column ingredients") {
                        id = "ingredients"
                        if (!ingredient.isEmpty()) {
                            img(src = icon8Url("edit", "ios-glyphs"), classes = "control hide") {
                                id = "make-editable"
                            }
                            img(src = icon8Url("cancel", "ios-glyphs"), classes = "control hide") {
                                id = "table-cancel"
                            }
                            img(src = icon8Url("save", "ios-glyphs"), classes = "control hide") {
                                id = "export-btn"
                            }
                            h3 {
                                +"Zutaten"
                            }
                            table("ingredients") {
                                tr("header hide") {
                                    th { +"amount" }
                                    th { +"measure" }
                                    th { }
                                    th { +"name" }
                                    th { }
                                }

                                each(ingredient) {
                                    insert(IngredientTemplate(), it)
                                }
                                tr("ingredient hide") {
                                    td("editable exportable") {
                                        style {
                                            textAlign = TextAlign.right
                                        }
                                    }
                                    td("editable exportable") {
                                        style {
                                            paddingRight = LinearDimension("10px")
                                        }
                                    }
                                    td("ingredient-icon") {
                                        style {
                                            paddingLeft = LinearDimension("10px")
                                        }
                                    }
                                    td("ingredient-name editable exportable") {

                                    }
                                    td(classes = "controls hide") {
                                        img(
                                            src = icon8Url("circled_chevron_down", set = "ios-glyphs"),
                                            classes = "table-down control"
                                        )
                                        img(
                                            src = icon8Url("slide_up", set = "ios-glyphs"),
                                            classes = "table-up control"
                                        )
                                        img(
                                            src = icon8Url("insert_row", set = "ios-glyphs"),
                                            classes = "table-add control"
                                        )
                                        img(
                                            src = icon8Url("delete_row", set = "ios-glyphs"),
                                            classes = "table-remove control"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    div("column") {
                        id = "instructions"
                        if (!step.isEmpty()) {
                            img(src = icon8Url("edit", "ios-glyphs"), classes = "control hide") {
                                id = "make-list-editable"
                            }
                            img(src = icon8Url("cancel", "ios-glyphs"), classes = "control hide") {
                                id = "list-cancel"
                            }
                            img(src = icon8Url("save", "ios-glyphs"), classes = "control hide") {
                                id = "list-save"
                            }
                            h3 {
                                +"Zubereitung"
                            }
                            ol("steps") {
                                each(step) {
                                    insert(StepTemplate(), it)
                                }
                                li("step") {
                                    id = "template"
                                    div("step-image") {
                                        icon8("add_image", set = "dusk", size = 150, classes = "largeIcon8")
                                    }
                                    p {
                                        +""
                                    }
                                }
                            }
                        }
                    }


                }
            }
        }
    }
}

class MainTemplate : Template<HTML> {
    val content = Placeholder<HtmlBlockTag>()
    val menu = TemplatePlaceholder<MenuTemplate>()
    override fun HTML.apply() {
        head {
            title { +"Cookbook" }
            meta { charset="utf-8" }
            link(rel = "stylesheet", href = "/static/css/main.css", type ="text/css")
//            link(rel = "stylesheet", href = "/static/styles.css", type = "text/css")
            link(href = "https://img.icons8.com/ios-glyphs/30/000000/cooking-book.png", rel = "shortcut icon", type = "image/x-icon")
        }
        body {
            div("page-wrap") {
                insert(MenuTemplate(), menu)

                section {
                    id = "main"
                    insert(content)
                    div("spinner")
                    footer {
                        id = "footer"
                        div("copyright") {
                            a(href = "https://icons8.com/") {
                                +"Free icons by Icons8"
                            }
                            +". "
                            a(href = "https://pixabay.com/") {
                                +"Free images by Pixabay"
                            }
                            +". "
                            a(href = "https://loading.io/spinner/double-rignt/") {
                                + "Spinner by loading.io"
                            }
                        }

                    }
                }
            }

            script(src = "/static/js/highlight.js") {}
            script(src = "/static/js/jquery.min.js") {}
//            script(src = "/static/js/jquery.poptrox.min.js") {}
            script(src = "/static/js/jquery.scrolly.min.js") {}
            script(src = "/static/js/jquery.caret.js") {}
            script(src = "/static/js/skel.min.js") {}
            script(src = "/static/js/util.js") {}
            script(src = "/static/js/custom.js") {}
            script(src = "/static/js/main.js") {}
        }
    }
}

class MenuTemplate : Template<FlowContent> {
    val item = PlaceholderList<UL, FlowContent>()
    override fun FlowContent.apply() {
        if (!item.isEmpty()) {
            nav {
                id = "nav"
                ul {
                    each(item) {
                        li {
                            insert(it)
                        }
                    }
                }
            }

        }
    }
}