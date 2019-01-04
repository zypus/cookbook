package com.zypus.datamodel

import com.amazonaws.services.s3.model.CannedAccessControlList
import com.beust.klaxon.internal.firstNotNullResult
import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.github.h0tk3y.betterParse.parser.Parser
import com.zypus.*
import com.zypus.api.WordType
import com.zypus.api.translate
import io.ktor.http.Url
import io.ktor.util.escapeHTML
import io.ktor.util.getDigestFunction
import io.ktor.util.hex
import org.apache.commons.lang3.RandomStringUtils
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import java.io.File
import java.io.InputStream

/**
 * TODO Add description
 *
 * @author zypus <zypus@t-online.de>
 *
 * @created 2018-12-08
 */

data class ExternalIngredient(val content: String)

data class ExternalInstruction(val text: String, val url: String?)

object IngredientsParser : Grammar<List<DataModel.Ingredient>>() {

    val newline by token("\n")
    val ws by token("\\s+", ignore = true)
    val num by token("\\d+(\\.\\d+)?")
    val dash by token("-")
    val slash by token("/")
    val g by token("g ")
    val l by token("l ")
    val ml by token("ml ")
    val ms by token("MS ")
    val tbl by token("EL ")
    val tsp by token("TL ")
    val pk by token("Pk ")
    val sg by token("sg ")
    val prise by token("p ")
    val word by token("[A-Za-zÄÖÜäöüß()\\[\\]-]+")

    val numParser by num use { text.toDouble() }

    val unit by g or l or ml or ms or tbl or tsp or pk or prise use { text.trim() }
    val ratio by numParser * -slash * numParser use {
        t1 / t2
    }
    val range by numParser * -dash * numParser use { t1..t2}
    val amount by (ratio or range or numParser) * optional(unit) use {
        when (t1) {
            is Double -> {
                val minMax = t1 as Double
                DataModel.Amount(minMax, minMax, t2.orEmpty())
            }
            is ClosedFloatingPointRange<*> -> {
                val range = t1 as ClosedFloatingPointRange<Double>
                DataModel.Amount(range.start, range.endInclusive, t2.orEmpty())
            }
            else -> DataModel.Amount(0.0, 0.0, "")
        }
    }
    val item by oneOrMore(word) use { this.joinToString(separator = " ") { it.text } }

    val ingredient by optional(amount) * item use {
        if (t1 != null) {
            DataModel.Ingredient(t2, t1!!)
        } else {
            DataModel.Ingredient(t2, DataModel.Amount(0.0, 0.0, ""))
        }
    }

    override val rootParser: Parser<List<DataModel.Ingredient>> by separated(ingredient, newline, acceptZero = true) use { terms }
}

object DataModel {

    val categoriesPath = File("storage/categories")

    val database: Database

    init {
        val (dbUrl, username, password) = jdbcConnectInfo()
        database = Database.connect(dbUrl, "org.postgresql.Driver", user = username, password = password)

        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.create(
                Users,
                Categories,
                Measures,
                Recipes,
                Ingredients,
                Steps,
                Notes,
                Icons,
                Terms,
                TermsIcons
            )
        }
    }

    fun validate(username: String, password: String): Int? {
        val usernameHasher = getDigestFunction("SHA-256", "42")
        return transaction {
            val user = User.find {
                Users.name eq hex(usernameHasher(username))
            }.firstOrNull()
            if (user == null) {
                null
            } else {
                val passwordHasher = getDigestFunction("SHA-256", user.salt)
                if (user.password == hex(passwordHasher(password))) user.id .value else null
            }
        }
    }

    fun getUsername(userId: Int): String {
        return transaction {
            User[userId].initials
        }
    }

    interface Category {
        val name: String
        val image: String?
        val recipes: Collection<Recipe>
    }

    interface Recipe {
        val name: String
        val author: String
        val yields: Int
        val yieldUnit: String
        val prepTime: Int
        val cookTime: Int
        val created: DateTime
        val updated: DateTime
        val image: String?
        val ingredients: Collection<Ingredient>
        val steps: Collection<Step>
    }

    data class Amount(val minValue: Double, val maxValue: Double, val unit: String, val scalar: Double = 1.0) {

        fun format(): String {
            val min = formatDouble(minValue)
            val max = formatDouble(maxValue)
            return if (min == max) {
                min
            } else {
                "$min-$max"
            }
        }

        private fun formatDouble(value: Double): String {
            return when {
                value <= 0.0 -> ""
                value.rem(value.toInt()) == 0.0 -> value.toInt().toString()
                else -> when {
                    value.rem(0.5) <= 0.01 -> "${(value / 0.5).toInt()}/2"
                    value.rem(0.25) <= 0.01 -> "${(value / 0.25).toInt()}/4"
                    value.rem(0.333) <= 0.01 -> "${(value / 0.333).toInt()}/3"
                    value.rem(0.125) <= 0.01 -> "${(value / 0.125).toInt()}/8"
                    else -> value.toString()
                }
            }
        }

    }

    data class Ingredient(val name: String, val amount: Amount)

    data class Step(val instruction: String, val type: String, val note: String? = null, val imageUrl: Url? = null)

    class CategoryDb(val id: Int, override val name: String, override val image: String?) :
        Category {
        override val recipes: Collection<Recipe>
            get() {
                return transaction {
                    CategoryEntity.findById(id)!!.recipes.map {
                        RecipeDb(
                            it.id.value,
                            it.name,
                            it.author.initials,
                            it.yields,
                            it.yieldUnit,
                            it.prepTime,
                            it.cookTime,
                            it.created,
                            it.edited,
                            it.image)
                    }
                }
            }
    }

    class RecipeDb(val id: Int,
                   override val name: String,
                   override val author: String,
                   override val yields: Int,
                   override val yieldUnit: String,
                   override val prepTime: Int,
                   override val cookTime: Int,
                   override val created: DateTime,
                   override val updated: DateTime,
                   override val image: String?) :
        Recipe {
        override val ingredients: Collection<Ingredient> by lazy {
            transaction {
                RecipeEntity.findById(id)!!.ingredients.sortedBy { it.number }.map {
                    val measure = it.measure
                    Ingredient(
                        it.name,
                        Amount(
                            it.minAmount.toDouble(),
                            it.maxAmount?.toDouble() ?: it.minAmount.toDouble(),
                            measure.symbol,
                            measure.scalar.toDouble()
                        )
                    )
                }
            }
        }

        override val steps: Collection<Step> by lazy {
            transaction {
                RecipeEntity.findById(id)!!.steps.sortedBy { it.number }.map {
                    Step(
                        it.instruction,
                        "dummy",
                        it.notes.firstOrNull()?.note,
                        it.image?.let { Url(it) })
                }
            }
        }

    }

    fun getCategories(): Collection<Category> {
        return transaction {
            CategoryEntity.all().map {
                CategoryDb(it.id.value, it.name, it.image)
            }
        }
    }

    fun getCategory(name: String): Category? {
        return transaction {
            CategoryEntity.find {
                Categories.name eq name
            }.firstOrNull()?.let {
                CategoryDb(it.id.value, it.name, it.image)
            }
        }
    }

    fun addRecipe(categoryName: String, recipe: String, authorId: Int) {
        transaction {
            val category = CategoryEntity.find {
                (Categories.name eq categoryName)
            }.first()

            if (category.recipes.find { it.name == recipe } != null) {
                throw Exception("Ein Rezept mit diesem Namen existiert bereits!")
            }

            RecipeEntity.new {
                this.name = recipe
                this.category = category
                this.author = User[authorId]
            }

        }
    }

    fun updateIngredients(category: String, recipe: String, ingredients: String) {

        val ingredientList = IngredientsParser.parseToEnd(ingredients)

        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }
            if (reci != null) {
                reci.ingredients.forEach {
                    it.delete()
                }
                ingredientList.forEachIndexed { index, ing ->
                    IngredientEntity.new {
                        this.name = ing.name
                        this.number = index + 1
                        this.minAmount = ing.amount.minValue.toFloat()
                        this.maxAmount =
                                ing.amount.maxValue.toFloat().takeUnless { ing.amount.minValue == ing.amount.maxValue }
                        val measures = MeasureEntity.find { Measures.symbol eq ing.amount.unit }
                        this.measure = if (!measures.empty()) measures.first() else throw Exception("Measure not found")
                        this.recipe = reci
                    }
                }
                reci.edited = DateTime.now()
            }
        }
    }

    fun updateInstructions(category: String, recipe: String, instructions: String) {
        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }
            if (reci != null) {
                reci.steps.forEach {
                    it.notes.forEach { it.delete() }
                    it.delete()
                }
                instructions.lines().forEachIndexed { index, inst ->
                    StepEntity.new {
                        this.instruction = inst
                        this.image = null
                        this.number = index + 1
                        this.recipe = reci
                    }
                }
                reci.edited = DateTime.now()
            }
        }
    }

    fun updateImage(category: String, recipe: String, imageName: String, inputStream: InputStream) {
        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }

            if (reci != null) {
                val s3Url = s3 {
                    val tempFile = createTempFile()

                    inputStream.copyTo(tempFile.outputStream())

                    var escapedName = imageName.escapeHTML()
                    val currentKeyLength =
                        "images/${category.escapeHTML()}/${recipe.escapeHTML()}//$escapedName}".length

                    if (currentKeyLength > 236) {
                        escapedName = escapedName.drop(currentKeyLength - 236)
                    }

                    val seed = RandomStringUtils.randomAlphanumeric(minOf(42, 256 - maxOf(currentKeyLength, 236)))

                    val key = "images/${category.escapeHTML()}/${recipe.escapeHTML()}/$seed/$escapedName}"
                    tempFile.upload(
                        key,
                        acl = CannedAccessControlList.PublicRead
                    )
                    url(key).toExternalForm()
                }

                if (reci.image != null) {
                    s3 {
                        delete(reci.image!!)
                    }
                }

                reci.image = s3Url
                reci.edited = DateTime.now()
            }

        }
    }

    fun updateTitle(category: String, recipe: String, title: String) {
        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }
            if (reci != null) {
                reci.name = title
                reci.edited = DateTime.now()
            }
        }
    }

    fun addCategory(name: String) {
        transaction {
            val category = CategoryEntity.find {
                (Categories.name eq name)
            }.firstOrNull()

            if (category != null) {
                throw Exception("Ein Kategorie mit diesem Namen existiert bereits!")
            }

            CategoryEntity.new {
                this.name = name
            }

        }
    }

    fun updateCategory(category: String, name: String) {
        transaction {
            val categoryEntity = CategoryEntity.find {
                (Categories.name eq category)
            }.firstOrNull()

            if (categoryEntity != null) {
                categoryEntity.name = name
            }
        }
    }

    fun iconForTerm(term: String, sets: List<String> = listOf("dusk", "color")): String {
        var iconEntity = transaction {
            IconEntity.find {
                Icons.term eq term.toLowerCase().escapeHTML() and Icons.set.inList(sets)
            }.firstOrNull()
        }
        if (iconEntity != null) {
            return iconEntity.url
        } else {
                val words = term.words()
            val candidates = if (words.size > 1) {
                    (listOf(term) + words).asSequence()
                } else {
                    listOf(term).asSequence()
                }.flatMap {
                    it.translate { DE to EN }.filter {
                        it.term.info.type == WordType.NOUN
                    }.asSequence()
                }.flatMap { translation ->
                translation.translations.asSequence()
            }.map {
                it.text
            }.flatMap {
                val transWords = it.words()
                if (transWords.size > 1) {
                    (listOf(it) + transWords).asSequence()
                } else {
                    listOf(it).asSequence()
                }
            }.toList().distinct()
            if (candidates.isNotEmpty()) {
                val termEntities = candidates.mapNotNull { candidate ->
                    val url = sets.firstNotNullResult { val icon8Url = checkedIcon8Url(candidate, set = it)
                        if (icon8Url != null) {
                            it to icon8Url
                        } else {
                            null
                        }
                    }
                    if (url != null) {
                        candidate to url
                    } else {
                        null
                    }
                }.map { (term, setAndUrl) ->
                    transaction {
                        TermEntity.find {
                            Terms.term eq term.toLowerCase()
                        }.firstOrNull() ?: TermEntity.new {
                            this.term = term.toLowerCase()
                            this.set = setAndUrl.first
                            this.url = setAndUrl.second
                        }
                    }
                }.distinctBy {
                    it.id.value
                }
                iconEntity = transaction {
                    IconEntity.new {
                        this.term = term.toLowerCase().escapeHTML()
                        this.translation = if (termEntities.isEmpty()) "broken link" else termEntities.first().term
                        this.set = if (termEntities.isEmpty()) sets.first() else termEntities.first().set
                        this.url = if (termEntities.isEmpty()) icon8Url(
                            "broken_link",
                            set = sets.first()
                        ) else termEntities.first().url
                    }
                }
                transaction {
                    iconEntity.candidates = SizedCollection(termEntities)
                }
                return iconEntity.url
            }
        }
        return icon8Url(
            "broken_link",
            set = sets.first()
        )
    }

    fun iconCandidatesForTerm(term: String, sets: List<String> = listOf("dusk", "color")): List<Pair<String, String>> {
        return transaction {
            val iconEntity = IconEntity.find {
                Icons.term eq term.toLowerCase().escapeHTML() and Icons.set.inList(sets)
            }.firstOrNull()
            iconEntity?.candidates?.map { it.term to it.url } ?: emptyList()
        }
    }

    fun updateTime(category: String, recipe: String, type: String, newTime: String) {
        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }
            if (reci != null) {
                when(type) {
                    "prep" -> reci.prepTime = newTime.toInt()
                    "cook" -> reci.cookTime = newTime.toInt()
                    else -> throw java.lang.Exception("Unknown time type: $type")
                }
                reci.edited = DateTime.now()
            }
        }
    }

    fun updateYield(category: String, recipe: String, newYield: String) {
        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }
            if (reci != null) {
                val (yields, yieldUnit) = newYield.split(" ", limit = 2)
                reci.yields = yields.toInt()
                reci.yieldUnit = yieldUnit.trim()
                reci.edited = DateTime.now()
            }
        }
    }

    fun updateIngredientIcon(category: String, recipe: String, index: Int, term: String) {
        transaction {
            val reci = CategoryEntity.find {
                (Categories.name eq category)
            }.first().recipes.find {
                it.name == recipe
            }
            if (reci != null) {
                val ingredientEntity = reci.ingredients.sortedBy { it.number }[index]
                val iconEntity = IconEntity.find {
                    Icons.term eq ingredientEntity.name.toLowerCase()
                }.first()
                val termEntity = iconEntity.candidates.first { it.term == term }
                iconEntity.translation = termEntity.term
                iconEntity.set = termEntity.set
                iconEntity.url = termEntity.url
            }
        }
    }
}

object Users : IntIdTable() {
    val name = varchar("name", 256).uniqueIndex()
    val salt = varchar("salt", 256)
    val password = varchar("password", 256)
    val initials = varchar("initials", 3)
    val role = varchar("role", 64)
}

object Categories : IntIdTable() {
    val name = varchar("name", 256)
    val image = varchar("image", 256).nullable()
}

object Ingredients : IntIdTable() {
    val name = varchar("name", 256)
    val number = integer("number")
    val minAmount = float("minAmount")
    val maxAmount = float("maxAmount").nullable()
    val measure = reference("measure", Measures)
    val recipe = reference("recipe", Recipes)
}

object Steps : IntIdTable() {
    val number = integer("number")
    val instruction = text("instruction")
    val image = varchar("image", 256).nullable()
    val recipe = reference("recipe", Recipes)
}

object Recipes : IntIdTable() {
    val name = varchar("name", 256).index()
    val image = varchar("image", 256).nullable()
    val created = date("created")
    val edited = datetime("edited")
    val yields = integer("yields").default(1)
    val yieldUnit = varchar("yieldunit", length = 256).default("Personen")
    val prepTime = integer("preptime").default(10)
    val cookTime = integer("cooktime").default(10)
    val category = reference("category", Categories)
    val author = reference("author", Users)
}

object Notes : IntIdTable() {
    val note = text("note")
    val step = reference("step", Steps)
}

object Measures : IntIdTable() {
    val name = varchar("name", 50)
    val symbol = varchar("symbol", 50)
    val scalar = float("scalar").default(1f)
}

object Icons : IntIdTable() {
    val term = varchar("term", 128).index()
    val set = varchar("set", 256).index()
    val translation = varchar("translation", 128)
    val url = varchar("url", 256)
}

object TermsIcons : Table(name = "terms__icons") {
    val term = reference("term", Terms).primaryKey(0)
    val icon = reference("icon", Icons).primaryKey(1)
}

object Terms : IntIdTable() {
    val term = varchar("term", 128)
    val set = varchar("set", 256)
    val url = varchar("url", 256)
}


class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var name by Users.name
    var salt by Users.salt
    var password by Users.password
    var initials by Users.initials
    var role by Users.role
    val recipies by RecipeEntity referrersOn Recipes.author
}

class CategoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CategoryEntity>(Categories)

    var name by Categories.name
    var image by Categories.image
    val recipes by RecipeEntity referrersOn Recipes.category
}

class IngredientEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IngredientEntity>(Ingredients)

    var name by Ingredients.name
    var number by Ingredients.number
    var minAmount by Ingredients.minAmount
    var maxAmount by Ingredients.maxAmount
    var measure by MeasureEntity referencedOn Ingredients.measure
    var recipe by RecipeEntity referencedOn Ingredients.recipe
}

class NoteEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<NoteEntity>(Notes)

    var note by Notes.note
    var step by StepEntity referencedOn Notes.step
}

class StepEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<StepEntity>(Steps)

    var number by Steps.number
    var instruction by Steps.instruction
    val notes by NoteEntity referrersOn Notes.step
    var image by Steps.image
    var recipe by RecipeEntity referencedOn Steps.recipe
}

class MeasureEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MeasureEntity>(Measures)

    var name by Measures.name
    var symbol by Measures.symbol
    var scalar by Measures.scalar
}

class RecipeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<RecipeEntity>(Recipes)

    var name by Recipes.name
    var image by Recipes.image
    var created by Recipes.created
    var edited by Recipes.edited
    var yields by Recipes.yields
    var yieldUnit by Recipes.yieldUnit
    var prepTime by Recipes.prepTime
    var cookTime by Recipes.cookTime
    var category by CategoryEntity referencedOn Recipes.category
    val ingredients by IngredientEntity referrersOn Ingredients.recipe
    val steps by StepEntity referrersOn Steps.recipe
    var author by User referencedOn Recipes.author
}

class TermEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TermEntity>(Terms)

    var term by Terms.term
    var set by Terms.set
    var url by Terms.url
}

class IconEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<IconEntity>(Icons)

    var term by Icons.term
    var url by Icons.url
    var set by Icons.set
    var translation by Icons.translation
    var candidates by TermEntity via TermsIcons
}