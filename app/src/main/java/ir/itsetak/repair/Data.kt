package ir.itsetak.repair

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Login data. The password is a WordPress "Application Password" (not the account password). */
data class Account(val site: String, val user: String, val pass: String)

object Prefs {
    private fun sp(ctx: Context): SharedPreferences {
        val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(
            ctx, "setak_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun load(ctx: Context): Account? {
        val p = sp(ctx)
        val site = p.getString("site", null) ?: return null
        val user = p.getString("user", null) ?: return null
        val pass = p.getString("pass", null) ?: return null
        return Account(site, user, pass)
    }

    fun save(ctx: Context, a: Account) {
        sp(ctx).edit().putString("site", a.site).putString("user", a.user).putString("pass", a.pass).apply()
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().clear().apply()
    }

    fun lastAttention(ctx: Context): Int = sp(ctx).getInt("last_attention", 0)

    fun setLastAttention(ctx: Context, n: Int) {
        sp(ctx).edit().putInt("last_attention", n).apply()
    }
}

class ApiException(message: String, val code: Int) : Exception(message)

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")
fun JSONObject.int(k: String): Int = optInt(k, 0)
fun JSONObject.bool(k: String): Boolean = optBoolean(k, false)
fun JSONObject.arr(k: String): JSONArray = optJSONArray(k) ?: JSONArray()
fun JSONObject.obj(k: String): JSONObject = optJSONObject(k) ?: JSONObject()

fun JSONArray.objects(): List<JSONObject> {
    val out = ArrayList<JSONObject>()
    for (i in 0 until length()) {
        val o = optJSONObject(i)
        if (o != null) out.add(o)
    }
    return out
}

fun JSONArray.strings(): List<String> {
    val out = ArrayList<String>()
    for (i in 0 until length()) out.add(optString(i, ""))
    return out
}

data class Option(val key: String, val label: String)

fun JSONArray.options(): List<Option> = objects().map { Option(it.str("key"), it.str("label")) }

/** Talks to /wp-json/setak-repair/v1 using HTTP Basic (Application Password). */
class SetakApi(acc: Account) {
    private val base = acc.site.trimEnd('/') + "/wp-json/setak-repair/v1"
    private val auth = "Basic " + Base64.encodeToString("${acc.user}:${acc.pass}".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun errorText(text: String, code: Int): String {
        if (code == 401 || code == 403) {
            return "ورود ناموفق است. نام کاربری یا «رمز برنامه» را بررسی کنید؛ اگر درست است، هاست ممکن است هدر Authorization را حذف کند (راهنمای README)."
        }
        return try {
            val m = JSONObject(text).optString("message", "")
            if (m.isNotEmpty()) m else "خطا ($code)"
        } catch (e: Exception) {
            if (code == 404) "API افزونه پیدا نشد (افزونه ۰٫۵ به بالا و پیوند یکتای وردپرس لازم است)." else "خطا ($code)"
        }
    }

    private suspend fun call(method: String, path: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val qs = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") {
                URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
            }
            val conn = URL(base + path + qs).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.setRequestProperty("Authorization", auth)
                conn.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                if (code !in 200..299) throw ApiException(errorText(text, code), code)
                text
            } finally {
                conn.disconnect()
            }
        }

    suspend fun ping(): JSONObject = JSONObject(call("GET", "/ping"))
    suspend fun meta(): JSONObject = JSONObject(call("GET", "/meta"))
    suspend fun dashboard(): JSONObject = JSONObject(call("GET", "/dashboard"))
    suspend fun attention(): JSONObject = JSONObject(call("GET", "/attention"))

    suspend fun repairs(search: String, status: String, page: Int): JSONObject {
        val q = HashMap<String, String>()
        if (search.isNotBlank()) q["search"] = search.trim()
        if (status.isNotBlank()) q["status"] = status
        q["page"] = page.toString()
        return JSONObject(call("GET", "/repairs", q))
    }

    suspend fun repair(id: Int): JSONObject = JSONObject(call("GET", "/repairs/$id"))

    suspend fun setStatus(id: Int, status: String, publicMessage: String, internalNote: String, holdReason: String): JSONObject {
        val b = JSONObject()
            .put("status", status)
            .put("public_message", publicMessage)
            .put("internal_note", internalNote)
            .put("hold_reason", holdReason)
        return JSONObject(call("POST", "/repairs/$id/status", body = b))
    }

    suspend fun setDiagnosis(id: Int, diagnosis: String, notes: String): JSONObject {
        val b = JSONObject().put("technician_diagnosis", diagnosis).put("internal_notes", notes)
        return JSONObject(call("POST", "/repairs/$id/diagnosis", body = b))
    }

    suspend fun create(body: JSONObject): JSONObject = JSONObject(call("POST", "/repairs", body = body))

    suspend fun lookup(phone: String): JSONArray = JSONArray(call("GET", "/lookup", mapOf("phone" to phone)))
}

/** Converts Persian/Arabic digits to Latin and strips everything that is not a digit. */
fun digitsOnly(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        when (c) {
            in '0'..'9' -> sb.append(c)
            in '\u06F0'..'\u06F9' -> sb.append('0' + (c - '\u06F0'))
            in '\u0660'..'\u0669' -> sb.append('0' + (c - '\u0660'))
            else -> {}
        }
    }
    return sb.toString()
}

fun money(n: Int): String = String.format(java.util.Locale.US, "%,d", n)
