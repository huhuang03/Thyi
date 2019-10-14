package com.th.thyi.demo.util

import android.util.Log

const val TAG = "tonghu"

fun logi(msg: String) {
    Log.i(TAG, msg)
}

fun logerr(e: Throwable) {
    Log.e(TAG, Log.getStackTraceString(e))
}
