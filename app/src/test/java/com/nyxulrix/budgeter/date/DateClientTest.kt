package com.nyxulrix.budgeter.date

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class DateClientTest {
    @Test fun numbersGetExactlyOneFirestoreType() {
        // Firestore rejects a value with two types set ("oneof field 'value_type' is already set").
        assertEquals(buildJsonObject { put("integerValue", "8140") }, DateClient.enc(JsonPrimitive(8140)))
        assertEquals(buildJsonObject { put("doubleValue", 1.5) }, DateClient.enc(JsonPrimitive(1.5)))
        assertEquals(buildJsonObject { put("stringValue", "Date") }, DateClient.enc(JsonPrimitive("Date")))
    }
}
