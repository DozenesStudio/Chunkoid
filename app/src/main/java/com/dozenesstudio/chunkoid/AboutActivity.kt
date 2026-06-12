package com.dozenesstudio.chunkoid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
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
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DozenesStudio/Chunkoid/releases"))
            startActivity(intent)
        }

        val btnDonate = findViewById<Button>(R.id.btn_donate)
        btnDonate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ifdian.net/p/5ef51b66663011f1b89352540025c377"))
            startActivity(intent)
        }

        val tvGithubLink = findViewById<TextView>(R.id.tv_github_link)
        tvGithubLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dozenesstudio.github.io/Chunkoid/"))
            startActivity(intent)
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