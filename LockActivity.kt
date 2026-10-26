package com.example.quizlock

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.widget.*
import java.io.File

class LockActivity : Activity() {
    private lateinit var root: LinearLayout
    private var target = ""

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sv = ScrollView(this)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        sv.addView(root); setContentView(sv)
        begin()
    }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); setIntent(i); begin() }
    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun begin() {
        target = intent.getStringExtra("pkg") ?: run { finish(); return }
        root.removeAllViews()
        val qs = Store.questions(this)
        if (Store.flag(this, "quiz") && qs.isNotEmpty()) question(qs.random()) else password()
    }

    private fun question(q: Q) {
        if (q.image != null) BitmapFactory.decodeFile(File(Store.dir(this), q.image).path)?.let {
            root.addView(ImageView(this).apply { setImageBitmap(it); adjustViewBounds = true })
        }
        root.addView(TextView(this).apply { text = q.text; textSize = 20f })
        if (q.mcq) {
            val g = RadioGroup(this)
            q.options.forEachIndexed { i, o -> g.addView(RadioButton(this).apply { text = o; id = i + 1 }) }
            root.addView(g)
            root.addView(btn("Submit") { result(q.check(g.checkedRadioButtonId.toString())) })
        } else {
            val et = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                hint = "Answer (up to 3 decimals)"
            }
            root.addView(et)
            root.addView(btn("Submit") { result(q.check(et.text.toString())) })
        }
    }

    private fun result(ok: Boolean) { if (ok) password() else { toast("Wrong - new question"); begin() } }

    private fun password() {
        root.removeAllViews()
        val et = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6)); hint = "Password"
        }
        root.addView(et)
        root.addView(btn(if (Store.flag(this, "timer")) "Unlock for 1 hour" else "Unlock") {
            if (Store.checkPw(this, et.text.toString())) {
                Store.unlock(this, target)
                packageManager.getLaunchIntentForPackage(target)?.let { startActivity(it) }
                finish()
            } else toast("Wrong password")
        })
    }
}
