package com.garmin.android.apps.clearshot.phone.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.garmin.android.apps.clearshot.phone.R

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        // Enable back button in action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.help)
        }

        // Set up click listeners for links
        setupResourceLinks()
    }

    private fun setupResourceLinks() {
        // GitHub Repository link
        findViewById<TextView>(R.id.link_github).setOnClickListener {
            openUrl("https://github.com/Caleb-Seely/Garmin-Remote-Camera")  // Replace with your actual GitHub repository URL
        }

        // Personal Website link
        findViewById<TextView>(R.id.link_website).setOnClickListener {
            openUrl("https://calebseely.com/")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}