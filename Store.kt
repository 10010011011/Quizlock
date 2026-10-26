package com.example.quizlock

import android.content.Context
import android.net.Uri
import android.widget.Button
import android.widget.Toast
import org.json.JSONArray
import java.io.BufferedInputStream
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.util.zip.ZipInputStream

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
fun Context.btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }

class Q(val text: String, val image: String?, val options: List<String>, val answer: String) {
    val mcq get() = options.isNotEmpty()
    // MCQ: answer is the 1-based option number. Numeric: accepts floor or ceiling at 3 decimals.
    fun check(input: String): Boolean {
        val s = input.trim()
        if (mcq) return s == answer
        return try {
            val v = BigDecimal(answer); val u = BigDecimal(s)
            u.compareTo(v.setScale(3, RoundingMode.FLOOR)) == 0 || u.compareTo(v.setScale(3, RoundingMode.CEILING)) == 0
        } catch (e: Exception) { false }
    }
}

object Store {
    private fun p(c: Context) = c.getSharedPreferences("q", Context.MODE_PRIVATE)
    private fun hash(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    fun hasPw(c: Context) = p(c).contains("pw")
    fun setPw(c: Context, pw: String) = p(c).edit().putString("pw", hash(pw)).apply()
    fun checkPw(c: Context, pw: String) = p(c).getString("pw", null) == hash(pw)
    fun validPw(s: String) = s.length in 4..6 && s.all { it.isDigit() }

    fun flag(c: Context, k: String) = p(c).getBoolean(k, true)
    fun setFlag(c: Context, k: String, v: Boolean) = p(c).edit().putBoolean(k, v).apply()
    fun locked(c: Context): Set<String> = p(c).getStringSet("apps", emptySet())!!.toSet()
    fun setLocked(c: Context, s: Set<String>) = p(c).edit().putStringSet("apps", s.toSet()).apply()

    fun expiry(c: Context, pkg: String) = p(c).getLong("u_$pkg", 0)
    fun isUnlocked(c: Context, pkg: String) = expiry(c, pkg) > System.currentTimeMillis()
    fun unlock(c: Context, pkg: String) {
        val e = if (flag(c, "timer")) System.currentTimeMillis() + 3_600_000 else Long.MAX_VALUE
        p(c).edit().putLong("u_$pkg", e).apply()
    }
    fun relock(c: Context, pkg: String) = p(c).edit().remove("u_$pkg").apply()
    fun unlockedPkgs(c: Context): List<String> = p(c).all.keys.filter { it.startsWith("u_") }.map { it.substring(2) }
    fun clearUnlocks(c: Context) { unlockedPkgs(c).forEach { relock(c, it) } }

    fun dir(c: Context) = File(c.filesDir, "qs")
    fun questions(c: Context): List<Q> = try {
        val a = JSONArray(File(dir(c), "questions.json").readText())
        (0 until a.length()).map { n ->
            val o = a.getJSONObject(n)
            val op = o.optJSONArray("options")
            Q(o.optString("text"), o.optString("image").ifEmpty { null },
                (0 until (op?.length() ?: 0)).map { i -> op!!.getString(i) }, o.getString("answer"))
        }
    } catch (e: Exception) { emptyList() }

    // Accepts a .zip (questions.json + images) or a bare .json
    fun import(c: Context, uri: Uri): Int {
        val d = dir(c); d.deleteRecursively(); d.mkdirs()
        val ins = BufferedInputStream(c.contentResolver.openInputStream(uri)!!)
        ins.mark(4)
        val zip = ins.read() == 'P'.code && ins.read() == 'K'.code
        ins.reset()
        if (zip) ZipInputStream(ins).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (!e.isDirectory) File(d, File(e.name).name).outputStream().use { z.copyTo(it) }
                e = z.nextEntry
            }
        } else ins.use { i -> File(d, "questions.json").outputStream().use { o -> i.copyTo(o) } }
        return questions(c).size
    }
}
