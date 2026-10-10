package com.jackwallner.football

import com.jackwallner.football.model.Player
import com.jackwallner.football.model.lenientList
import kotlinx.serialization.json.Json
import org.junit.Test

class ParsePerfTest {
    @Test
    fun parsePage() {
        val text = javaClass.getResource("/snapshot-page.json")!!.readText()
        repeat(3) {
            val t0 = System.nanoTime()
            val el = Json.parseToJsonElement(text)
            val t1 = System.nanoTime()
            val players = el.lenientList(Player::fromJson)
            val t2 = System.nanoTime()
            println("parse ${(t1 - t0) / 1_000_000}ms map ${(t2 - t1) / 1_000_000}ms players=${players.size}")
        }
    }
}
