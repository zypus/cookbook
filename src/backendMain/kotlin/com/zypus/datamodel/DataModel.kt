package com.zypus.datamodel

import com.amazonaws.services.s3.model.CannedAccessControlList
import com.beust.klaxon.Klaxon
import com.zypus.jdbcConnectInfo
import com.zypus.s3
import io.ktor.http.Url
import io.ktor.util.escapeHTML
import io.ktor.util.getDigestFunction
import io.ktor.util.hex
import org.apache.commons.lang3.RandomStringUtils
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
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

data class ExternalIngredient(val amount: String, val measure: String, val name: String)

data class ExternalInstruction(val text: String, val url: String?)

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
                Notes
            )
            if(CategoryEntity.count() == 0) {
                getDefaultCategories().forEach {
                    key, cat->
                    val category = CategoryEntity.new {
                        this.name = cat.name
                    }

                    cat.recipes.forEach {
                        reci ->
                        val recipe = RecipeEntity.new {
                            this.name = reci.name
                            this.category = category
                        }

                        reci.ingredients.forEachIndexed {
                            index, ing ->
                            IngredientEntity.new {
                                this.name = ing.name
                                this.number = index + 1
                                this.amount = ing.amount.value.toFloat()
                                val measures = MeasureEntity.find { Measures.symbol eq ing.amount.unit }
                                this.measure = if (!measures.empty()) measures.first() else throw Exception("Measure not found")
                                this.recipe = recipe
                            }
                        }
                        reci.steps.forEachIndexed {
                            index, s ->
                            val step = StepEntity.new {
                                this.number = index + 1
                                this.instruction = s.instruction
                                this.recipe = recipe
                            }
                            if (s.note != null) {
                                NoteEntity.new {
                                    this.note = s.note
                                    this.step = step
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun validate(username: String, password: String): Boolean {
        val usernameHasher = getDigestFunction("SHA-256", "42")
        return transaction {
            val user = User.find {
                Users.name eq hex(usernameHasher(username))
            }.firstOrNull()
            if (user == null) {
                false
            } else {
                val passwordHasher = getDigestFunction("SHA-256", user.salt)
                user.password == hex(passwordHasher(password))
            }
        }
    }

    interface Category {
        val name: String
        val image: String?
        val recipes: Collection<Recipe>
    }

    data class CategoryImpl(override val name: String, override val image: String?):
        Category {
        override val recipes: List<RecipeProxy>
        get() {
            val recipiesPath = File(categoriesPath, name)
            val recipeNames = recipiesPath.list()
            return recipeNames.map { name ->
                RecipeProxy(File(recipiesPath, name))
            }
        }
    }

    interface Recipe {
        val name: String
        val image: String?
        val ingredients: Collection<Ingredient>
        val steps: Collection<Step>
    }

    class RecipeProxy(val recipePath: File): Recipe {

        val recipe: LoadedRecipe by lazy {
            Klaxon().parse<LoadedRecipe>(recipePath)!!
        }

        val id: String
            get() = recipePath.nameWithoutExtension
        override val name: String
            get() = recipe.name
        override val image: String?
            get() = recipe.image
        override val ingredients: List<Ingredient>
            get() = recipe.ingredients
        override val steps: List<Step>
            get() = recipe.steps
    }

    data class LoadedRecipe(override val name: String,
                            override val image: String?, override val ingredients: List<Ingredient>, override val steps: List<Step>):
        Recipe

    data class Amount(val value: Double,val unit: String, val scalar: Double = 1.0)

    data class Ingredient(val name: String, val amount: Amount)

    data class Step(val instruction: String, val type: String, val note: String? = null, val imageUrl: Url? = null)

    fun getDefaultCategories(): Map<String, CategoryImpl> {
        val categoryNames = categoriesPath.list()
        return categoryNames.associate { name ->
            name to CategoryImpl(name, null)
        }
    }

    class CategoryDb(val id: Int, override val name: String, override val image: String?):
        Category {
        override val recipes: Collection<Recipe>
            get() {
                return transaction {
                    CategoryEntity.findById(id)!!.recipes.map {
                        RecipeDb(it.id.value, it.name, it.image)
                    }
                }
            }
    }

    class RecipeDb(val id: Int, override val name: String, override val image: String?):
        Recipe {
        override val ingredients: Collection<Ingredient> by lazy {
            transaction {
                RecipeEntity.findById(id)!!.ingredients.sortedBy { it.number }.map {
                    val measure = it.measure
                    Ingredient(
                        it.name,
                        Amount(
                            it.amount.toDouble(),
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

    fun addRecipe(categoryName: String, recipe: String) {
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
            }

        }
    }

    fun updateIngredients(category: String, recipe: String, ingredients: List<ExternalIngredient>) {
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
                ingredients.forEachIndexed { index, ing ->
                    IngredientEntity.new {
                        this.name = ing.name
                        this.number = index + 1
                        this.amount = ing.amount.toFloat()
                        val measures = MeasureEntity.find { Measures.symbol eq ing.measure }
                        this.measure = if (!measures.empty()) measures.first() else throw Exception("Measure not found")
                        this.recipe = reci
                    }
                }
                reci.edited = DateTime.now()
            }
        }
    }

    fun updateInstructions(category: String, recipe: String, instructions: List<ExternalInstruction>) {
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
                instructions.forEachIndexed { index, inst ->
                    StepEntity.new {
                        this.instruction = inst.text
                        this.image = inst.url?.takeUnless { "icons8" in it }
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

}

object Users : IntIdTable() {
    val name = varchar("name", 256).uniqueIndex()
    val salt = varchar("salt", 256)
    val password = varchar("password", 256)
    val initials = varchar("initials", 3)
    val role = varchar("role", 64)
}

object Categories: IntIdTable() {
    val name = varchar("name", 256)
    val image = varchar("image", 256).nullable()
}

object Ingredients: IntIdTable() {
    val name = varchar("name", 256)
    val number = integer("number")
    val amount = float("amount")
    val measure = reference("measure", Measures)
    val recipe = reference("recipe", Recipes)
}

object Steps: IntIdTable() {
    val number = integer("number")
    val instruction = text("instruction")
    val image = varchar("image", 256).nullable()
    val recipe = reference("recipe", Recipes)
}

object Recipes : IntIdTable() {
    val name = varchar("name", 256).index()
    val image = varchar("image", 256).nullable()
    val created = date("created")
    val edited = date("edited")
    val category = reference("category", Categories)
}

object Notes: IntIdTable() {
    val note = text("note")
    val step = reference("step", Steps)
}

object Measures: IntIdTable() {
    val name = varchar("name", 50)
    val symbol = varchar("symbol", 50)
    val scalar = float("scalar").default(1f)
}

object Icons: IntIdTable() {
    val term = varchar("term", 128)
    val translation = varchar("translation", 128)
    val vetoed = text("vetoed").default("")
}


class User(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<User>(Users)

    var name by Users.name
    var salt by Users.salt
    var password by Users.password
    var initials by Users.initials
    var role by Users.role
}

class CategoryEntity(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<CategoryEntity>(Categories)

    var name by Categories.name
    var image by Categories.image
    val recipes by RecipeEntity referrersOn Recipes.category
}

class IngredientEntity(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<IngredientEntity>(Ingredients)

    var name by Ingredients.name
    var number by Ingredients.number
    var amount by Ingredients.amount
    var measure by MeasureEntity referencedOn Ingredients.measure
    var recipe by RecipeEntity referencedOn Ingredients.recipe
}

class NoteEntity(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<NoteEntity>(Notes)

    var note by Notes.note
    var step by StepEntity referencedOn Notes.step
}

class StepEntity(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<StepEntity>(Steps)

    var number by Steps.number
    var instruction by Steps.instruction
    val notes by NoteEntity referrersOn Notes.step
    var image by Steps.image
    var recipe by RecipeEntity referencedOn Steps.recipe
}

class MeasureEntity(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<MeasureEntity>(Measures)

    var name by Measures.name
    var symbol by Measures.symbol
    var scalar by Measures.scalar
}

class RecipeEntity(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<RecipeEntity>(Recipes)

    var name by Recipes.name
    var image by Recipes.image
    var created by Recipes.created
    var edited by Recipes.edited
    var category by CategoryEntity referencedOn Recipes.category
    val ingredients by IngredientEntity referrersOn Ingredients.recipe
    val steps by StepEntity referrersOn Steps.recipe
}