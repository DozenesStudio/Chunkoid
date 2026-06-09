package com.dozenesstudio.chunkoid

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.dozenesstudio.chunkoid.utils.ToastUtils

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.navigationIcon?.setTint(getColor(R.color.white))

        val btnCheckUpdate = findViewById<Button>(R.id.btn_check_update)
        btnCheckUpdate.setOnClickListener {
            ToastUtils.show(this, getString(R.string.update_latest))
        }

        val btnDonate = findViewById<Button>(R.id.btn_donate)
        btnDonate.setOnClickListener {
            ToastUtils.show(this, getString(R.string.feature_not_available))
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}