package com.dozenesstudio.chunkoid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.dozenesstudio.chunkoid.utils.ToastUtils

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        applyNoElevationToCards()

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

        val tvchunkerLink = findViewById<TextView>(R.id.tv_chunker_link)
        tvchunkerLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HiveGamesOSS/Chunker"))
            startActivity(intent)
        }

        val tvnbtLink = findViewById<TextView>(R.id.tv_nbt_link)
        tvnbtLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/PowerNukkit/NBT-Manipulator"))
            startActivity(intent)
        }

        val tvsdkLink = findViewById<TextView>(R.id.tv_sdk_link)
        tvsdkLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Dicecan/NetEaseDecryptorSDK"))
            startActivity(intent)
        }

        val tvwebsiteLink = findViewById<TextView>(R.id.tv_website_link)
        tvwebsiteLink.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chunkoid.top"))
            startActivity(intent)
        }

        val ivBilibili = findViewById<ImageView>(R.id.iv_bilibili)
        ivBilibili.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://space.bilibili.com/3537108083935954"))
            startActivity(intent)
        }

        val ivGithub = findViewById<ImageView>(R.id.iv_github)
        ivGithub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DozenesStudio"))
            startActivity(intent)
        }
    }

    private fun applyNoElevationToCards() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        removeCardElevation(rootView)
    }

    private fun removeCardElevation(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is com.google.android.material.card.MaterialCardView) {
                child.cardElevation = 0f
            } else if (child is ViewGroup) {
                removeCardElevation(child)
            }
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