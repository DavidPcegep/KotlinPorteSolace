/**
 * Auteur: David Pigeon
 * Date: 5 mai 2023
 * Version: 1.0
 * Description: Cette activité permet à l'utilisateur de se connecter à l'application.
 */

package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import kotlinx.android.synthetic.main.activity_history.*
import kotlinx.android.synthetic.main.activity_login.*
import com.google.android.material.snackbar.Snackbar
import io.github.cdimascio.dotenv.dotenv
import okhttp3.*
import java.io.IOException
import java.util.*

class LoginActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    @SuppressLint("SetTextI18n", "AppCompatMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        toolbar!!.title = getString(R.string.app_name)
        setSupportActionBar(toolbar)
        button.setOnClickListener {
            val user = editTextTextPersonName.text.toString()
            val pass = editTextTextPassword.text.toString()
            verifyConnection(user, pass)
            Log.d("user", user)
            Log.d("pass", pass)
        }

        parametre.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
        }

    }

    fun updateLocale(act: LoginActivity, s: String) {
        val languageCode = getLanguageCodeFromPreference() // obtenez le code de langue à partir des préférences de l'utilisateur
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
        recreate()
    }

    private fun getLanguageCodeFromPreference(): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        return sharedPreferences.getString("language_code", "fr") ?: "fr"
    }
    fun verifyConnection(user: String, pass: String) {
        val dotenv = dotenv {
            directory = "/assets"
            filename = "env" // instead of '.env', use 'env'
        }
        val token = dotenv["TOKEN"]

        // find the url in shared preferences
        val sharedPreferences = getSharedPreferences("sharedPrefs", MODE_PRIVATE)
        var url = sharedPreferences.getString("URL", null)
        url = if (url == null) "http://167.114.96.59:2223/api/authenticate/$user/$pass"
        else "$url/api/authenticate/$user/$pass"

        // Verify if the url is valid
        val request = token?.let {
            try {
                Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
            } catch (e: IllegalArgumentException) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Connection impossible... Vérifiez l'adresse de votre l'API dans les paramètres",
                    Snackbar.LENGTH_LONG
                ).show()
                return
            }
        }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Adresse invalide",
                    Snackbar.LENGTH_LONG
                ).show()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "Adresse invalide",
                            Snackbar.LENGTH_LONG
                        ).show()
                        throw IOException("Unexpected code $response")
                    }
                    if(response.body()!!.string() == "true"){
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                    }
                    else{
                        Snackbar.make(
                            findViewById(android.R.id.content),
                            "Information invalide",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }

                }
            }
        })

    }

}