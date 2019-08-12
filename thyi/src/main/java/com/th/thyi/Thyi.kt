package com.th.thyi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.Observable.create
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * 接口请求类
 * 可以通过构造函数传入cookieJar来管理cookie。默认没有cookie
 *
 * 可以设置TAG。来改变输出的tag
 * Created by yi on 2/22/16.
 */
class Thyi {
    private var okClient: OkHttpClient? = null
    private val JSON = "application/json; charset=utf-8".toMediaTypeOrNull()
    private lateinit var options: ThyiOptions

    @JvmOverloads constructor(cookieJar: CookieJar = CookieJar.NO_COOKIES, options: ThyiOptions = ThyiOptions()) {
        this.options = options
        this.okClient = OkHttpClient.Builder().cookieJar(cookieJar)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()
    }

    /**
     * 使用CookieJar
     * Will be delete soon
     */
    @Deprecated("")
    constructor(okClient: OkHttpClient) {
        this.okClient = okClient
        throw Exception("this constructor is deprecated")
    }

    @JvmOverloads fun<T> get(url: String, params: Map<String, String> = emptyMap(), clazz: Class<T>): Observable<T> {
        return request("GET", url, params, clazz)
    }

    fun <T> postJson(url: String, json: String, clazz: Class<T>): Observable<T> {
        val body = RequestBody.create(JSON, json)
        return request(Request.Builder().url(url).post(body).build(), clazz)
    }

    fun<T> postJson(url: String, params: Map<String, String>, clazz: Class<T>): Observable<T> {
        val body = RequestBody.create(JSON, JSONObject(params).toString())
        return request(Request.Builder().url(url).post(body).build(), clazz)
    }

    fun <T> request(url: String, params: Map<String, String>, clazz: Class<T>): Observable<T> {
        return request("POST", url, params, clazz)
    }

    fun <T> request(method: String, url: String, params: Map<String, String>?, clazz: Class<T>, headers: Map<String, String> = emptyMap()): Observable<T> {
        return requestInternal(method, url, params, clazz, headers)
    }

    fun requestImage(url: String, param: Map<String, String>): Observable<Bitmap> {
        return request("GET", url, param, Bitmap::class.java)
    }

    /**
     * 下载文件
     */
    fun requestFile(url: String): Observable<InputStream> {
        return requestInternal("GET", url, emptyMap(), InputStream::class.java, emptyMap())
    }

    private fun <T> requestInternal(method: String, url: String,
                                    param: Map<String, String>?, clazz: Class<T>,
                                    headers: Map<String, String>): Observable<T> {
        val postBuilder = FormBody.Builder()

        var finalUrl = url

        if ("GET".equals(method, true)) {
            finalUrl = packageGetParam(url, param)
        } else {
            Log.i(TAG, "thyi param: $param")
            if (param != null) {
                for (key in param.keys) {
                    if (param[key] != null) {
                        postBuilder.add(key, param[key]!!)
                    }
                }
            }
        }

        var refer = ""

        try {
            val aURL = URL(url)
            refer = aURL.protocol + "://" + aURL.host
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        val rb = Request.Builder()
                .url(finalUrl)
                .header("referer", refer)
                .header("user-agent", "Mozilla/5.0 (Linux; Android 5.0; SM-G900P Build/LRX21T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Mobile Safari/537.36")

        for (header in headers) {
            rb.header(header.key, header.value)
        }

        if (!"GET".equals(method, true)) {
            rb.post(postBuilder.build())
            rb.method(method, postBuilder.build())
        }

        val request = rb.build()

        return request(request, clazz)

    }

    fun <T> request(request: Request, clazz: Class<T>): Observable<T> {
        Log.i(TAG, "send: " + request.url.toString())
        val onSubsribe = ObservableOnSubscribe<T> { e ->
            try {
                Log.i(TAG, "send on thread; ${Thread.currentThread().name}")
                val response = okClient!!.newCall(request).execute()
                Log.i(TAG, "onResponse")
                when (clazz) {
                    InputStream::class.java -> {
                        e.onNext(response.body!!.byteStream() as T)
                        e.onComplete()
                    }
                    Response::class.java -> {
                        e.onNext(response as T)
                        e.onComplete()
                    }
                    Bitmap::class.java -> {
                        val inputStream = response.body!!.byteStream()
                        var bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
                        if (bitmap == null) {
                            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                        }
                        e.onNext(bitmap as T)
                        e.onComplete()
                    }
                    else -> {
                        val rst = response.body!!.string()
                        Log.i(TAG, "rst: " + rst + "\n\t for url: " + request.url)
                        val bean: T

                        bean = when (clazz) {
                            String::class.java -> rst as T
                            JSONObject::class.java -> JSONObject(rst) as T
                            else ->  {
                                if (rst == null || rst == "") {
                                    clazz.newInstance()
                                } else {
                                    Gson().fromJson(rst, clazz)
                                }
                            }
                        }

                        e.onNext(bean)
                        e.onComplete()
                    }
                }

            } catch (ex: Exception) {
                ex.printStackTrace()
                e.onError(ex)
                e.onComplete()
            }
        }
        val subscribeOn = create(onSubsribe).subscribeOn(Schedulers.io())
        if (options.autoCallbackOnMain) {
            subscribeOn.observeOn(AndroidSchedulers.mainThread())
        }
        return subscribeOn
    }

    private fun packageGetParam(url: String, params: Map<String, String>?): String {
        var url = url
        if (params == null || params.isEmpty()) return url

        val sb = StringBuilder()
        sb.append(url)
        if (!url.contains("?")) {
            sb.append("?")
        }

        for ((key, value) in params) {
            sb.append(key).append("=").append(value).append("&")
        }
        url = sb.toString()
        url = url.substring(0, url.length - 1)
        return url
    }

    companion object {
        val TAG = "thyi"
    }

}

class ThyiOptions {
    /**
     * 是否在网络请求返回的时候自动切换到主进程
     */
    var autoCallbackOnMain = true
}
