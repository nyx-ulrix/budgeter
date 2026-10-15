package com.nyxulrix.budgeter.core

import com.nyxulrix.budgeter.data.AppState
import com.nyxulrix.budgeter.data.MacroGoals
import com.nyxulrix.budgeter.data.Meal
import com.nyxulrix.budgeter.data.Txn
import com.nyxulrix.budgeter.data.mergeRemote
import com.nyxulrix.budgeter.data.toUpload
import com.nyxulrix.budgeter.data.unloggedFood
import com.nyxulrix.budgeter.data.withoutTxn
import com.nyxulrix.budgeter.ai.Food
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MacrosTest {
    @Test fun reportAveragesLoggedDaysAndCountsTargets() {
        val byDay = mapOf(
            "2026-10-14" to listOf(mapOf("calories" to 800.0, "protein" to 40.0), mapOf("calories" to 900.0, "protein" to 70.5)),
            "2026-10-12" to listOf(mapOf("calories" to 2500.0, "protein" to 60.0)),
        )
        val (days, stats) = macroReport(byDay, listOf("calories", "protein"), mapOf("calories" to 2000.0, "protein" to 100.0), LocalDate.parse("2026-10-14"), 7)
        assertEquals(listOf("2026-10-14", "2026-10-12"), days.map { it.first })   // the empty day in between is left out
        assertEquals(1700.0, days[0].second["calories"])
        assertEquals(2100.0, stats[0].average, 0.0)
        assertEquals(1, stats[0].onTargetDays)          // calories is a limit: 1700 yes, 2500 no
        assertEquals(1, stats[1].onTargetDays)          // protein is a minimum: 110.5 yes, 60 no
        assertEquals("1,820", fmtMacro(1820.0))
        assertEquals("12.5", fmtMacro(12.5))
    }

    @Test fun foodExpensesWaitUntilLoggedAndMealsSync() {
        val food = Txn(id = "f", date = "2026-10-14", total = 650, category = "Food")
        val groceries = Txn(id = "g", date = "2026-10-14", total = 3000, category = "Groceries")
        val st = AppState(txns = listOf(food, groceries))
        assertEquals(listOf("f"), st.unloggedFood("2026-10-14").map { it.id })
        val logged = st.copy(meals = listOf(Meal(id = "m", date = "2026-10-14", name = "Chicken rice", txnId = "f", macros = mapOf("calories" to 600.0))),
            macros = MacroGoals(tracked = listOf("calories", "fat")))
        assertEquals(emptyList<Txn>(), logged.unloggedFood("2026-10-14"))
        val web = AppState().mergeRemote(logged.toUpload(emptyMap()))
        assertEquals(logged.meals, web.meals)
        assertEquals(listOf("calories", "fat"), web.macros.tracked)
        assertEquals(emptyList<Meal>(), logged.withoutTxn("f").meals)   // deleting the expense removes its meal
    }

    @Test fun readsUsdaResultPer100gAndScales() {
        val f = Json.parseToJsonElement("""{"description":"WHEY PROTEIN ISOLATE","brandOwner":"Acme","servingSize":30,"servingSizeUnit":"g",
            "foodNutrients":[{"nutrientId":1062,"unitName":"kJ","value":1500},{"nutrientId":1008,"unitName":"KCAL","value":370},
            {"nutrientId":1003,"unitName":"G","value":80},{"nutrientId":1093,"unitName":"MG","value":500}]}""").jsonObject
        val h = Food.readHit(f)!!
        assertEquals("Whey protein isolate · Acme", h.name)
        assertEquals(mapOf("calories" to 370.0, "protein" to 80.0, "sodium" to 500.0), h.per100)
        assertEquals(30.0, h.serving)
        assertEquals(mapOf("calories" to 111.0, "protein" to 24.0, "sodium" to 150.0), Food.scale(h.per100, 30.0))
        assertNull(Food.readHit(Json.parseToJsonElement("""{"description":"Water","foodNutrients":[]}""").jsonObject))
    }
}
