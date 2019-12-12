package com.th.thyi.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.th.thyi.inner.ThyiEx
import com.th.thyi.demo.api.BaiduApi
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val baiduApi = ThyiEx("http://www.baidu.com").create(BaiduApi::class.java)
        baiduApi.home()
                .map {
                    tv.text = it
                    Log.i("tonghu", "Thread; ${Thread.currentThread().name}")
                    it
                }
                .subscribe({ }, { Log.i("tonghu", Log.getStackTraceString(it)) }, { Log.i("tonghu", "onFinish") })
    }
}
