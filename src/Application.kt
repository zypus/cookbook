package com.zypus

import com.beust.klaxon.Klaxon
import com.zypus.datamodel.DataModel
import com.zypus.datamodel.ExternalIngredient
import com.zypus.datamodel.ExternalInstruction
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.basic
import io.ktor.features.*
import io.ktor.html.respondHtmlTemplate
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.locations.*
import io.ktor.request.path
import io.ktor.request.receiveMultipart
import io.ktor.request.receiveText
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.sessions.Sessions
import io.ktor.sessions.cookie
import io.ktor.util.asStream
import io.ktor.util.escapeHTML
import kotlinx.css.CSSBuilder
import kotlinx.css.Image
import kotlinx.html.*
import org.slf4j.event.Level
import kotlin.collections.set

//import io.ktor.client.features.auth.basic.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val sslEnabled = System.getenv("SSL_ENABLED") == "true"

    if (sslEnabled) {
        install(XForwardedHeaderSupport)
        install(HttpsRedirect)
    }

    install(Locations) {
    }

    install(Sessions) {
        cookie<MySession>("MY_SESSION") {
            cookie.extensions["SameSite"] = "lax"
        }
    }

    install(AutoHeadResponse)

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor") // will send this header with each response
    }

    install(Authentication) {
        basic("myBasicAuth") {
            realm = "Ktor Server"
            validate {
                if (DataModel.validate(it.name, it.password)) UserIdPrincipal(it.name) else null }
        }
    }

    routing {
//        get("/html-dsl") {
//            call.respondHtml {
//                body {
//                    h1 { +"HTML" }
//                    ul {
//                        for (n in 1..10) {
//                            li { +"$n" }
//                        }
//                    }
//                }
//            }
//        }

//        get("/s3") {
//            call.respondText {
//                StringBuilder().apply {
//                    com.zypus.s3 {
//                        objects.objectSummaries.forEach {
//                            this@apply.appendln(it.key)
//                        }
//                    }
//                }.toString()
//            }
//        }


//        get("/session/increment") {
//            val session = call.sessions.get<MySession>() ?: MySession()
//            call.sessions.set(session.copy(count = session.count + 1))
//            call.respondText("Counter is ${session.count}. Refresh to increment.")
//        }

//        get("/db") {
//            call.respondText(System.getenv("DATABASE_URL"))
//        }
//
//        get("/redirect_image") {
//            call.respondRedirect("/static/ktor_logo.svg")
//        }
//
//        get("/test_image") {
//            call.respondHtml {
//                body {
//                    img(src = "/redirect_image")
//                }
//            }
//        }

        authenticate("myBasicAuth") {

            get("/styles.css") {
                call.respondCss {
                    rule(":root") {
                        declarations["--highlight-color"] = "#${StyleGuide.highlightColor}"
                        declarations["--accent-color"] = "#${StyleGuide.accentColor}"
                    }
                }
            }

            get<Index> {
                val categories = DataModel.getCategories()

                call.respondHtmlTemplate(CookbookTemplate()) {
                    categories.forEach { cat ->
                        val categoryName = cat.name.capitalize()
                        categoriesUrl = application.locations.href(Categories())
                        bannerImageUrl = ImageManager.getImageUrlFor("mediterranean_cuisine", lang = "en", size = ImageManager.ImageSize.LARGE)
                        category {
                            val categoryHref = application.locations.href(Categories.Category(cat.name.escapeHTML()))
                            a(href = categoryHref) {
                                style {
                                    backgroundImage =
                                            Image("url(${cat.image ?: ImageManager.getImageUrlFor(cat.name) ?: ImageManager.getImageUrlFor("recipe")})")
                                }
                            }
                            h3 {
                                +categoryName
                            }
                        }
                    }
                }

            }

            get<Categories> {
                val categories = DataModel.getCategories()

                call.respondHtmlTemplate(GalleryTemplate()) {
                    galleryName = "Kategorien"
                    isEditable = false
                    menuList = arrayListOf(
                        "categories" to application.locations.href(Categories())
                    )

                    categories.forEach { cat ->
                        val categoryName = cat.name.capitalize()

                        galleryItem {
                            val categoryHref = application.locations.href(Categories.Category(cat.name.escapeHTML()))
                            a(href = categoryHref) {
                                style {
                                    backgroundImage = Image("url(${cat.image ?: ImageManager.getImageUrlFor(cat.name)
                                    ?: ImageManager.getImageUrlFor("recipe")})")
                                }
                            }
                            h3 {
                                +categoryName
                            }
                        }
                    }

                    footer {
                        a(
                            href = application.locations.href(Categories.Category("new")),
                            classes = "button big"
                        ) {
                            +"Neue Kategorie hinzufügen"
                        }
                    }

                }

            }

            get<Categories.Category> { categoryLoc ->

                val category = DataModel.getCategory(categoryLoc.categoryName)

                if (category != null) {
                    call.respondHtmlTemplate(GalleryTemplate()) {
                        galleryName = category.name.capitalize()
                        menuList = arrayListOf(
                            "categories" to application.locations.href(Categories()),
                            "category" to application.locations.href(categoryLoc)
                        )

                        category.recipes.forEach { reci ->
                            galleryItem {
                                val recipeHref =
                                    application.locations.href(
                                        Categories.Category.Recipe(
                                            categoryLoc,
                                            reci.name.escapeHTML()
                                        )
                                    )
                                a(href = recipeHref) {
                                    style {
                                        backgroundImage =
                                                Image("url(${reci.image ?: ImageManager.getImageUrlFor(reci.name)
                                                ?: ImageManager.getImageUrlFor("recipe")})")
                                    }
                                }
                                h3 {
                                    +reci.name.capitalize()
                                }
                            }
                        }

                        footer {
                            a(href = application.locations.href(Categories.Category.Recipe(categoryLoc, "new")), classes = "button big") {
                                +"Neues Rezept hinzufügen"
                            }
                        }
                    }

                } else if (categoryLoc.categoryName == "new") {
                    call.respondHtmlTemplate(GalleryTemplate()) {
                        galleryName = "Neue Kategorie"
                        isEditable = true
                        isNew = true
                        menuList = arrayListOf(
                            "categories" to application.locations.href(Categories()),
                            "category" to application.locations.href(categoryLoc)
                        )
                    }
                } else {
                    throw Exception("Unbekannte Kategorie ${categoryLoc.categoryName}")
                }

            }

            get<Categories.Category.Recipe> { recipeLoc ->
                val category = DataModel.getCategory(recipeLoc.category.categoryName)

                if (category != null) {
//                    val session = call.sessions.get<MySession>() ?: MySession()
//                    call.sessions.set(session.copy(currentRecipe = recipeLoc))

                    val recipies = category.recipes
                    val actualRecipe = recipies.find { it.name == recipeLoc.name }
                    if (actualRecipe != null) {
                        val colors = actualRecipe.ingredients.mapIndexed { index, ingredient ->
                            ingredient.name to "highlight$index"
                        }.toMap()
                        call.respondHtmlTemplate(RecipeTemplate()) {

                            recipeName = actualRecipe.name.capitalize()
                            recipeImageUrl = actualRecipe.image ?: ImageManager.getImageUrlFor(actualRecipe.name, size=ImageManager.ImageSize.LARGE) ?: ImageManager
                                    . getImageUrlFor ("recipe", ImageManager.ImageSize.LARGE)!!

                            imageUploadUrl = application.locations.href(recipeLoc) + "/image"

                            menuList = arrayListOf(
                                "categories" to application.locations.href(Categories()),
                                "category" to application.locations.href(recipeLoc.category),
                                "recipe" to application.locations.href(recipeLoc)
                            )

                            category {
                                a(href = application.locations.href(Categories.Category(category.name.escapeHTML()))) {
                                    +category.name.capitalize()
                                }
                            }
                            recipe {
                                a(href = application.locations.href(recipeLoc), classes = "active") {
                                    +actualRecipe.name.capitalize()
                                }
                            }
                            if (actualRecipe.ingredients.isNotEmpty()) {
                                actualRecipe.ingredients.forEach { i ->
                                    ingredient {
                                        ingredientImage {
                                            val term = i.name.lastWord().toLowerCase()
                                            val definition = robustDefintion(term)
                                            println("$term: $definition")
                                            val translatedTerm = definition ?: "ingredients"
                                            icon8(translatedTerm, set = "dusk", altSet = "color", altTerm = "ingredients", classes = "icons8")
                                        }
                                        ingredientName {
                                            span(colors[i.name]) {
                                                +i.name.capitalize()
                                            }
                                        }
                                        amountValue {
                                            if (i.amount.value.rem(i.amount.value.toInt()) == 0.0) {
                                                +i.amount.value.toInt().toString()
                                            } else {
                                                +i.amount.value.toString()
                                            }
                                        }
                                        amountUnit {
                                            +i.amount.unit
                                        }

                                    }
                                }
                            } else {
                                defaultIngredient()
                            }
                            if (actualRecipe.steps.isNotEmpty()) {
                                actualRecipe.steps.forEach { s ->
                                    step {
                                        stepImage {
                                            icon8(
                                                robustDefintion(s.instruction.firstWord()) ?: "ingredients",
                                                classes = "largeIcon8",
                                                size = 150,
                                                set = "dusk",
                                                altSet = "color",
                                                altTerm = "ingredients"
                                            )
                                        }
                                        stepInstruction {
                                            highlighted(s.instruction, colors)
                                        }
                                    }
                                }
                            } else {
                                defaultStep()
                            }
                        }
                    } else if (recipeLoc.name == "new") {
                        call.respondHtmlTemplate(RecipeTemplate()) {

                            isNew = true

                            recipeName = "Neues leckeres Rezept"
                            recipeImageUrl = ImageManager.getImageUrlFor("recipe", size = ImageManager.ImageSize.LARGE)!!

                            imageUploadUrl = application.locations.href(recipeLoc) + "/image"

                            menuList = arrayListOf(
                                "categories" to application.locations.href(Categories()),
                                "category" to application.locations.href(recipeLoc.category),
                                "recipe" to application.locations.href(recipeLoc)
                            )

                            category {
                                a(href = application.locations.href(Categories.Category(category.name.escapeHTML()))) {
                                    +category.name.capitalize()
                                }
                            }
                            recipe {
                                a(href = application.locations.href(recipeLoc), classes = "active") {
                                    +recipeName
                                }
                            }
                        }
                    }
                } else {
                    throw Exception("${recipeLoc.name} doesn't exist in categoryName ${recipeLoc.category.categoryName}")
                }

            }

            post<Categories.Category.Title> { loc ->

                val categoryLoc = loc.category

                val klaxon = Klaxon()
                val json = call.receiveText()

                data class Title(val title: String)

                val title = klaxon.parse<Title>(json)

                if (title != null) {
                    if (title.title.escapeHTML() == categoryLoc.categoryName) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        val processedTitle =
                            title.title.words().map { it.toLowerCase().capitalize() }.joinToString(separator = " ")

                        if (categoryLoc.categoryName == "new") {
                            DataModel.addCategory(processedTitle)
                        } else {
                            DataModel.updateCategory(categoryLoc.categoryName, processedTitle)
                        }

                        call.respondRedirect(application.locations.href(categoryLoc.copy(categoryName = processedTitle)))
                    }
                }

            }

            post<Categories.Category.Recipe.Title> { loc ->

                val recipeLoc = loc.recipe

                val klaxon = Klaxon()
                val json = call.receiveText()

                data class Title(val title: String)

                val title = klaxon.parse<Title>(json)

                if (title != null) {
                    if(title.title.escapeHTML() == recipeLoc.name) {
                        call.respond(HttpStatusCode.OK)
                    } else {
                        val processedTitle =
                            title.title.words().map { it.toLowerCase().capitalize() }.joinToString(separator = " ")

                        if (recipeLoc.name == "new") {
                           DataModel.addRecipe(recipeLoc.category.categoryName, processedTitle)
                        } else {
                            DataModel.updateTitle(recipeLoc.category.categoryName, recipeLoc.name, processedTitle)
                        }

                        call.respondRedirect(application.locations.href(recipeLoc.copy(name = processedTitle)))
                    }
                }

            }

            post<Categories.Category.Recipe.Ingredients> { loc ->

                val recipeLoc = loc.recipe

                if (recipeLoc.name == "new") {
                    call.respond("Das Rezept muss zuerst benannt werden, bevor Zutaten hinzugefügt werden können.")
                }

                val klaxon = Klaxon()
                val json = call.receiveText()
                val newIngredients = klaxon.parseArray<ExternalIngredient>(json)

                if (newIngredients != null) {
                    DataModel.updateIngredients(recipeLoc.category.categoryName, recipeLoc.name, newIngredients)
                }

                call.respond(HttpStatusCode.OK)

            }

            post<Categories.Category.Recipe.Instructions> { loc ->

                val recipeLoc = loc.recipe

                if (recipeLoc.name == "new") {
                    call.respond("Das Rezept muss zuerst benannt werden, bevor Zubereitungsschritte hinzugefügt werden können.")
                }

                val klaxon = Klaxon()
                val json = call.receiveText()
                println(json)
                val newInstructions = klaxon.parseArray<ExternalInstruction>(json)

                if (newInstructions != null) {
                    DataModel.updateInstructions(recipeLoc.category.categoryName, recipeLoc.name, newInstructions)
                }

                call.respond(HttpStatusCode.OK)

            }

            post<Categories.Category.Recipe.Image> { loc ->

                val recipeLoc = loc.recipe

                if (recipeLoc.name == "new") {
                    call.respond("Das Rezept muss zuerst benannt werden, bevor ein Bild hinzugefügt werden kann.")
                }

                val multipart = call.receiveMultipart()

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            println("FormItem(${part.name},${part.value})")
                        }
                        is PartData.FileItem -> {
                            DataModel.updateImage(
                                category = recipeLoc.category.categoryName,
                                recipe = recipeLoc.name,
                                imageName = part.originalFileName ?: "unnamed",
                                inputStream = part.provider().asStream()
                            )
                        }
                        is PartData.BinaryItem -> {
                            println("BinaryItem(${part.name})")
                        }
                    }

                    part.dispose()
                }

                call.respondRedirect(application.locations.href(recipeLoc))

            }

//            get<StepImage> { imageLoc ->
//                val recipeLoc = call.sessions.get<MySession>()?.currentRecipe
//                if (recipeLoc != null) {
//                    val categoryName = DataModel.getCategoryName(recipeLoc.categoryName.categoryName)
//                    if (categoryName != null) {
//                        val recipe = categoryName.recipes.find { it.name == recipeLoc.name }
//
//                        if (recipe != null) {
//                            val step = recipe.steps.elementAtOrNull(imageLoc.index)
//                            val url = when {
//                                step?.imageUrl != null -> step.imageUrl.toString()
//                                step != null -> icon8Url(
//                                    step.instruction.words().take(3).joinToString(separator = "_") { it.toLowerCase() },
//                                    size = 150,
//                                    set = "dusk"
//                                )
//                                else -> icon8Url("photo", size=150)
//                            }
//                            call.respondRedirect(url)
//                        }
//                    }
//                }
//            }

//            get("/protected/route/basic") {
//                val principal = call.principal<UserIdPrincipal>()!!
//                call.respondText("Hello ${principal.name}")
//            }

            // Static feature. Try to access `/static/ktor_logo.svg`
            static("/static") {
                resources("static")
            }
        }
    }
}

private fun RecipeTemplate.defaultIngredient() {
    ingredient {
        ingredientImage {
            icon8("love", set = "dusk", classes = "icons8")
        }
        ingredientName {
            +"Liebe"
        }
        amountValue {
            +"1"
        }
        amountUnit {
            +"g"
        }
    }
}

private fun RecipeTemplate.defaultStep() {
    step {
        stepImage {
            icon8(
                "task_completed",
                classes = "largeIcon8",
                size = 150,
                set = "dusk"
            )
        }
        stepInstruction {
            +"Fertig!!!"
        }
    }
}

@Location("/images/step_image/{index}")
class StepImage(val index: Int)

@Location("/")
class Index()

@Location(path = "/categories")
class Categories {
    @Location("/{categoryName}")
    data class Category(val categoryName: String) {
        @Location(path = "/title")
        data class Title(val category: Category)
        @Location("/{name}")
        data class Recipe(val category: Category, val name: String) {
            @Location(path = "/title")
            data class Title(val recipe: Recipe)
            @Location(path = "/image")
            data class Image(val recipe: Recipe)
            @Location(path = "/ingredients")
            data class Ingredients(val recipe: Recipe)
            @Location(path = "/instructions")
            data class Instructions(val recipe: Recipe)
        }

    }
}


data class MySession(val currentRecipe: Categories.Category.Recipe? = null)

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
