package com.example.myapplication


import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toolbar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.model.Serrures
import com.example.myapplication.mqtt.MqttClientHelper
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import okhttp3.*
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.io.IOException
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.log


class MainActivity : AppCompatActivity() {

    var value = "";
    var isScanned = false
    private var tag = ""
    private val client = OkHttpClient()
    private val mqttClient by lazy {
        MqttClientHelper(this)
    }

        // Redirection vers l'activité de l'historique

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textViewMsgPayload.movementMethod = ScrollingMovementMethod()
        setMqttCallBack()
        getSerrures()
        // initialize 'num msgs received' field in the view

        btnHistory.setOnClickListener {
            val intent = Intent(this@MainActivity, HistoryActivity::class.java)
            startActivity(intent)
        }

        Timer("SettingSub", false).schedule(2000) {
            if (mqttClient.isConnected()) {
                val topic = "porte_sub"
                Thread.sleep(1000)
                mqttClient.subscribe(topic)
            }
        }

        Timer("CheckMqttConnection", false).schedule(3000) {
            if (!mqttClient.isConnected()) {
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Connection au serveur MQTT perdue",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

    }

    override fun onBackPressed() {
        return
    }
    private fun setMqttCallBack() {
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(b: Boolean, s: String) {
                val snackbarMsg = "Connected to host:\n'$SOLACE_MQTT_HOST'."
                Log.w("Debug", snackbarMsg)
                Snackbar.make(findViewById(android.R.id.content), snackbarMsg, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }
            override fun connectionLost(throwable: Throwable) {
                val snackbarMsg = "Connection to host lost:\n'$SOLACE_MQTT_HOST'"
                Log.w("Debug", snackbarMsg)
                Snackbar.make(findViewById(android.R.id.content), snackbarMsg, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            }
            @SuppressLint("SetTextI18n")
            @Throws(Exception::class)
            override fun messageArrived(topic: String, mqttMessage: MqttMessage) {
                Log.w("Debug", "Message received from host '$SOLACE_MQTT_HOST': $mqttMessage")
                tag = "";
                if (mqttMessage.toString() != "true" || mqttMessage.toString() != "false") {
                    tag = "$mqttMessage\n"
                    CompareParseValueToSub(tag)
                    textViewMsgPayload.text = tag
                }

                if (mqttMessage.toString().contains("C089 true")) {
                    Log.d("Debug", "Oui")
                    textViewMsgSerrure1Status.text = "Ouvert"
                    textViewMsgSerrure1Status.setBackgroundColor(Color.parseColor("#00FF00"))
                }
                if (mqttMessage.toString().contains("C089 false")) {
                    Log.d("Debug", "Non")
                    textViewMsgSerrure1Status.text = "Fermé"
                    textViewMsgSerrure1Status.setBackgroundColor(Color.parseColor("#FF0000"))
                }
            }

            override fun deliveryComplete(iMqttDeliveryToken: IMqttDeliveryToken) {
                Log.w("Debug", "Message published to host '$SOLACE_MQTT_HOST'")
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this@MainActivity, SettingActivity::class.java))

        }
        return true
    }

    override fun onDestroy() {
        mqttClient.destroy()
        super.onDestroy()
    }

    fun getSerrures(): ArrayList<String> {
        val request = Request.Builder()
            .url("http://167.114.96.59:2223/api/getSerrures")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    value = response.body()!!.string()
                    val gson = Gson()
                    val serrures = gson.fromJson(value, Array<Serrures>::class.java).toList()
                    runOnUiThread {
                        for (serrure in serrures) {
                            if (serrure.id == "C089") {
                                textViewMsgSerrure1.text = serrure.name
                            }
                        }
                    }
                }
            }
        })
        }

    //Permet de récupérer la liste des utilisateurs et de les afficher dans la liste "userList"
    @SuppressLint("SetTextI18n")
    fun CompareParseValueToSub (tagScan: String){
        val request = Request.Builder()
            .url("http://167.114.96.59:2223/api/verifyTag/$tagScan")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    value = response.body()!!.string()
                    if (mqttClient.isConnected()){
                        var isSend = true
                        if (isSend && value != "false"){
                            val topic = "porte_sub"
                            mqttClient.publish(topic, value)
                            isSend = false
                            saveToLog(tagScan, value)
                            Log.d("Debug", value)
                        }
                    }
                }

            }
        })
    }

    fun saveToLog(tagScan: String, value: String){
        val tagToSend = tagScan
        val user = "Un utilisateur"
        //Get DateTime
        val c = Calendar.getInstance().time
        //Url encode
        val url = "http://167.114.96.59:2223/api/saveToLogs/$user/$tagToSend/$value/$c"
        Log.d("DebugURL", url)
        val parseURL = url.replace(" ", "%20").replace("\n", "")
        Log.d("Debug", parseURL)
        //Send to server
        val request = Request.Builder()
            .url(parseURL)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    Log.d("SendToAPI", "$user $value $tagToSend $c")
                }
            }
        })
        }

    }



