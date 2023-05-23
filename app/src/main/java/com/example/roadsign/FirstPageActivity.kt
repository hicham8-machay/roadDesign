package com.example.roadsign

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class FirstPageActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_firstpage)
        val ctn = findViewById<Button>(R.id.ctn)
        ctn.setOnClickListener {

            val intent = Intent(this@FirstPageActivity, SignInActivity::class.java)
            startActivity(intent)
        }
    }
    }
