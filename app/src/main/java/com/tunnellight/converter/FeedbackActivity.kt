package com.tunnellight.converter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import com.google.android.material.button.MaterialButton

/** A simple screen where users type feedback and new conversion requests in-app. */
class FeedbackActivity : AppCompatActivity() {

    private lateinit var feedbackInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        title = getString(R.string.feedback_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        feedbackInput = findViewById(R.id.feedbackInput)
        findViewById<MaterialButton>(R.id.sendFeedbackButton).setOnClickListener { sendFeedback() }
    }

    /** Open the user's email app pre-filled with the feedback typed in the app. */
    private fun sendFeedback() {
        val message = feedbackInput.text.toString().trim()
        if (message.isEmpty()) {
            Toast.makeText(this, getString(R.string.feedback_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val email = getString(R.string.feedback_email)
        // Encode the subject and body into the mailto URI itself: some email
        // apps (e.g. Gmail) ignore EXTRA_SUBJECT/EXTRA_TEXT for ACTION_SENDTO.
        val mailto = "mailto:$email" +
            "?subject=" + Uri.encode(getString(R.string.feedback_subject)) +
            "&body=" + Uri.encode(message)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = mailto.toUri()
            // Keep the extras too, for apps that prefer them.
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.feedback_subject))
            putExtra(Intent.EXTRA_TEXT, message)
        }
        try {
            startActivity(intent)
            finish()
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.feedback_no_email, email), Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
