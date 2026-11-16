package cl.grupo28.hestia.data.gmail

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import cl.grupo28.hestia.domain.WeekRange
import cl.grupo28.hestia.domain.gmail.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.*
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

class EncryptedGmailStore(context: Context, name: String = "hestia-gmail-results") : GmailStore {
    init { require(name.matches(Regex("[a-zA-Z0-9-]+"))) }
    private val file = AtomicFile(File(context.noBackupFilesDir, name))
    private val alias = "hestia.gmail.$name.v1"
    private fun key(): SecretKey {
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keys.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun load(ownerId: String): GmailSnapshot? {
        if (!file.baseFile.exists()) return null
        try {
            val bytes = file.openRead().use { it.readBytes() }
            require(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            if (json.getString("owner") != ownerId) { clear(); return null }
            val array = json.getJSONArray("records")
            require(array.length() <= 200)
            val records = (0 until array.length()).map { i ->
                val row = array.getJSONObject(i); val reasons = row.getJSONArray("reasons")
                EmailRecord(row.getString("id"), Instant.parse(row.getString("received")), row.getString("sender"), row.getString("subject"),
                    EmailAnalysis(row.getInt("score"), EmailRisk.valueOf(row.getString("level")), (0 until reasons.length()).map { reasons.getString(it) }), row.optBoolean("limited"))
            }
            return GmailSnapshot(ownerId, WeekRange(LocalDate.parse(json.getString("week")), ZoneId.of(json.getString("zone"))), Instant.parse(json.getString("checked")), records, json.getBoolean("partial"))
        } catch (_: Exception) { clear(); throw java.io.IOException("Gmail results could not be restored") }
    }
    override fun save(snapshot: GmailSnapshot) {
        val records = JSONArray()
        snapshot.records.forEach { record -> records.put(JSONObject().put("id",record.fingerprint).put("received",record.receivedAt.toString())
            .put("sender",record.sender).put("subject",record.subject).put("score",record.analysis.score).put("level",record.analysis.level.name)
            .put("reasons",JSONArray(record.analysis.reasons)).put("limited",record.limitedContent)) }
        val json = JSONObject().put("owner",snapshot.ownerId).put("week",snapshot.week.start.toString()).put("zone",snapshot.week.zone.id)
            .put("checked",snapshot.checkedAt.toString()).put("partial",snapshot.partial).put("records",records)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val bytes = cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch(error: Exception) { file.failWrite(stream); throw error }
    }
    override fun clear() { file.delete(); check(!file.baseFile.exists()) }
}
