package io.github.laozhudu.linkplay.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JsonTest {
    @Test
    fun `parses nested structures`() {
        val doc = Json.parse("""{"a":[1,2,{"b":true}],"c":null,"d":"x\ny"}""") as JsonValue.Obj
        val arr = doc.array("a")!!
        assertEquals(3, arr.items.size)
        assertEquals(1.0, (arr.items[0] as JsonValue.Num).value, 0.0)
        assertEquals(true, ((arr.items[2] as JsonValue.Obj).fields["b"] as JsonValue.Bool).value)
        assertEquals("x\ny", doc.string("d"))
        assertEquals(JsonValue.Null, doc["c"])
    }

    @Test
    fun `rejects trailing garbage`() {
        assertThrows(JsonParseException::class.java) { Json.parse("""{"a":1} junk""") }
    }

    @Test
    fun `rejects unterminated string`() {
        assertThrows(JsonParseException::class.java) { Json.parse("""{"a":"oops}""") }
    }
}
