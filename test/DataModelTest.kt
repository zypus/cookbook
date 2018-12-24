package com.zypus

//import io.ktor.client.features.auth.basic.*
import org.junit.Test
import kotlin.test.assertEquals

class DataModelTest {
    @Test
    fun testJsonParsing() {
        val categories = DataModel.getCategories()
        assertEquals(categories.values, listOf(DataModel.Category("backing")))
        val recipe = categories["backing"]!!.recipies[0]

        assertEquals(recipe.name, "Coffee Beans")
    }
}
