package cl.grupo28.hestia

import cl.grupo28.hestia.domain.gmail.EmailRiskEngine
import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*
class SharedRiskFixturesTest {
    @Test fun androidAndBackendUseTheSameRiskFixtures() {
        val raw=requireNotNull(javaClass.classLoader?.getResourceAsStream("email-risk-fixtures.json")).bufferedReader().use{it.readText()}.removePrefix("\uFEFF")
        val fixtures=JSONArray(raw);val engine=EmailRiskEngine()
        repeat(fixtures.length()){i->val f=fixtures.getJSONObject(i);val a=engine.analyze(f.getString("subject"),f.getString("body"),f.getString("sender"),f.getString("replyTo"));assertEquals(f.getString("name"),f.getInt("score"),a.score);assertEquals(f.getString("name"),f.getString("level"),a.level.name)}
    }
}
