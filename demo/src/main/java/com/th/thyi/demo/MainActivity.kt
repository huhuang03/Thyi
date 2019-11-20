package com.th.thyi.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.th.thyi.Thyi_Old
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val thyi = Thyi_Old()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        thyi.request("http://www.baidu.com", emptyMap(), String::class.java)
                .map {
                    tv.text = it
                    Log.i("tonghu", "Thread; ${Thread.currentThread().name}")
                    it
                }
                .subscribe({ }, { Log.i("tonghu", Log.getStackTraceString(it)) }, { Log.i("tonghu", "onFinish") })
    }
}
