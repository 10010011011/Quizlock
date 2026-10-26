package com.example.quizlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var authed = false
    private var keep = false
    private val pwType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        setContentView(root)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }
    override fun onResume() { super.onResume(); keep = false; render() }
    override fun onStop() { super.onStop(); if (!keep) authed = false }

    private fun render() {
        root.removeAllViews()
        if (!Store.hasPw(this) || !authed) gate() else panel()
    }

    private fun gate() {
        val setup = !Store.hasPw(this)
        val et = EditText(this).apply {
            inputType = pwType; filters = arrayOf(InputFilter.LengthFilter(6))
            hint = if (setup) "New password (4-6 digits)" else "Password"
        }
        root.addView(et)
        root.addView(btn(if (setup) "Set password" else "Open settings") {
            val s = et.text.toString()
            if (setup) {
                if (Store.validPw(s)) { Store.setPw(this, s); authed = true; render() } else toast("Use 4-6 digits")
            } else if (Store.checkPw(this, s)) { authed = true; render() } else toast("Wrong password")
        })
    }

    private fun panel() {
        val c = this
        fun sw(label: String, key: String) = Switch(c).apply {
            text = label; isChecked = Store.flag(c, key)
            setOnCheckedChangeListener { _, v -> Store.setFlag(c, key, v); Store.clearUnlocks(c) }
        }
        root.addView(sw("Ask a question before password", "quiz"))
        root.addView(sw("1-hour timer (off = stays unlocked)", "timer"))
        root.addView(btn("Import questions (.zip/.json) - ${Store.questions(c).size} loaded") {
            keep = true
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 1)
        })
        root.addView(btn("Turn on app guard (Accessibility)") { keep = true; startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(btn("Change password") { changePw() })

        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinct().sortedBy { it.second.lowercase() }
        val sel = Store.locked(c).toMutableSet()
        val lv = ListView(c)
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(i: Int) = apps[i]
            override fun getItemId(i: Int) = i.toLong()
            override fun getView(i: Int, v: View?, p: ViewGroup?): View = CheckBox(c).apply {
                text = apps[i].second; isChecked = apps[i].first in sel
                setOnCheckedChangeListener { _, on ->
                    if (on) sel.add(apps[i].first) else sel.remove(apps[i].first)
                    Store.setLocked(c, sel)
                }
            }
        }
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun changePw() {
        val et = EditText(this).apply { inputType = pwType; filters = arrayOf(InputFilter.LengthFilter(6)); hint = "New password (4-6 digits)" }
        AlertDialog.Builder(this).setView(et).setPositiveButton("Save") { _, _ ->
            val s = et.text.toString()
            if (Store.validPw(s)) { Store.setPw(this, s); toast("Saved") } else toast("Use 4-6 digits")
        }.show()
    }

    override fun onActivityResult(r: Int, res: Int, d: Intent?) {
        super.onActivityResult(r, res, d)
        val u = d?.data
        if (r == 1 && res == RESULT_OK && u != null) {
            try { toast("${Store.import(this, u)} questions loaded") } catch (e: Exception) { toast("Import failed: ${e.message}") }
        }
    }
}
