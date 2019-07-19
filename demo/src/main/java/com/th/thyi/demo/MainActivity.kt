package com.th.thyi.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.th.thyi.Thyi

class MainActivity : AppCompatActivity() {
    val thyi = Thyi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thyi.request("http://192.168.1.176:6002/api/public/registerOrLogin", emptyMap(), String::class.java)
                .subscribe({ Log.i("tonghu", it) }, { Log.i("tonghu", Log.getStackTraceString(it)) }, { Log.i("tonghu", "onFinish") })
    }
}
