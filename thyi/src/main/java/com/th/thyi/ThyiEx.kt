package com.th.thyi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.th.thyi.annotation.GET
import com.th.thyi.annotation.HEADER
import com.th.thyi.annotation.POST
import com.th.thyi.annotation.QUERY
import io.reactivex.Observable
import io.reactivex.Observable.create
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import org.json.JSONObject
import java.io.InputStream
import java.lang.IllegalStateException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.lang.reflect.Type
import java.net.URL
import java.util.concurrent.TimeUnit

data class RequestOption(val annotations: MutableList<Annotation>, val returnType: Type) {
    fun getHeaders(): List<HEADER> {
        return annotations.filterIsInstance<HEADER>()
    }

    fun getMethod(): Annotation {
        val method = annotations.filter { it is POST || it is GET }
        check(method.size == 1) { "Can and only have one request method. " }
        return method[0]
    }

    fun isGet(): Boolean {
        return getMethod() is GET
    }

    fun isPost(): Boolean {
        return getMethod() is POST
    }


    fun parsePath(): String {
        var partUrl = when(val method = getMethod()) {
            is GET -> method.path
            is POST -> method.path
            else -> throw IllegalStateException("Can and only have one request method. ")
        }

        annotations.filterIsInstance(QUERY::class.java)
    }

    fun parseInnerType(): Type {

    }

    fun parseReturnRawType(): Type {
        return if (returnType is ParameterizedType) {
            returnType.rawType
        } else {
            returnType
        }
    }

    fun buildRequest(): Request {

//        val postBuilder = FormBody.Builder()
//        var finalUrl = url
//        if ("GET".equals(method, true)) {
//            finalUrl = packageGetParam(url, param)
//        } else {
//            Log.i(Thyi.TAG, "thyi param: $param")
//            if (param != null) {
//                for (key in param.keys) {
//                    if (param[key] != null) {
//                        postBuilder.add(key, param[key]!!)
//                    }
//                }
//            }
//        }

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

        return rb.build()
    }

    private fun packageGetParam(url: String, params: Map<String, String>?): String {
        if (params == null || params.isEmpty()) return url

        val sb = StringBuilder(url)
        if (!url.contains("?")) {
            sb.append("?")
        }

        return sb.toString() + params.map { "${it.key}=${it.value}" }.joinToString("&")
    }

    fun getQuery(): List<QUERY> {
        return annotations.
    }
}

/**
 * 接口请求类
 * 可以通过构造函数传入cookieJar来管理cookie。默认没有cookie
 *
 * 可以设置TAG。来改变输出的tag
 * Created by yi on 2/22/16.
 */
class Thyi {
    fun <T>create(define: Class<T>): T {
        return Proxy.newProxyInstance(define.classLoader, arrayOf(define)) { proxy: Any, method: Method, args: Array<Any?> ->
            // do the method
            doProxyRequest(proxy, method, args)
        } as T
    }

    fun doProxyRequest(proxy: Any, method: Method, args: Array<Any?>): Any? {
        return null
    }

    private fun doRequest(requestOption: RequestOption): Observable<Any> {
        val returnRawType = requestOption.parseReturnRawType()
        val innerType = requestOption.parseInnerType()
        require(returnRawType == Observable::class.java) { "can only return Observable" }
        val request = requestOption.buildRequest()

        val onSubscribe = ObservableOnSubscribe<Any> { e ->
            try {
                Log.i(TAG, "send on thread; ${Thread.currentThread().name}")
                val response = okClient!!.newCall(request).execute()
                Log.i(TAG, "onResponse")

                val data = when (innerType) {
                    InputStream::class.java -> {
                        response.body()!!.byteStream()
                    }
                    Response::class.java -> {
                        response
                    }
                    Bitmap::class.java -> {
                        val inputStream = response.body()!!.byteStream()
                        BitmapFactory.decodeStream(inputStream)?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                    }
                    else -> {
                        val str = response.body()!!.string()
                        Log.i(TAG, "rst: " + str + "\n\t for url: " + request.url())

                         when (innerType) {
                            String::class.java -> str
                            JSONObject::class.java -> JSONObject(str)
                            else ->  {
                                Gson().fromJson(str, innerType)
                            }
                        }
                    }
                }

                e.onNext(data)
                e.onComplete()
            } catch (ex: Exception) {
                ex.printStackTrace()
                e.onError(ex)
            }
        }

        var subscribeOn = create(onSubscribe).subscribeOn(Schedulers.io())
        if (options.autoCallbackOnMain) {
            subscribeOn = subscribeOn.observeOn(AndroidSchedulers.mainThread())
        }
        return subscribeOn

    }

    fun <T> request(request: Request, clazz: Class<T>): Observable<T> {
        Log.i(TAG, "send: " + request.url().toString())
    }

    private var okClient: OkHttpClient? = null
    private val JSON = MediaType.parse("application/json; charset=utf-8")
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

    fun requestImage(url: String, param: Map<String, String> = emptyMap()): Observable<Bitmap> {
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
    }


    companion object {
        val TAG = "thyi"
    }

}

