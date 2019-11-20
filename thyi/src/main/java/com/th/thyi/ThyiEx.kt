package com.th.thyi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import com.th.thyi.Thyi.Companion.TAG
import com.th.thyi.annotation.*
import io.reactivex.Observable
import io.reactivex.Observable.create
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import org.json.JSONObject
import java.io.InputStream
import java.lang.IllegalStateException
import java.lang.reflect.*
import java.net.URL
import java.util.concurrent.TimeUnit

data class MethodParam(val annotations: List<Annotation>, val value: String) {
    fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean {
        return annotations.isNotEmpty() && annotations.filterIsInstance(annotationClass).isNotEmpty()
    }

    /**
     * get single annotation
     */
    fun <T>annotation(annotationClass: Class<T>): T where T: Annotation {
        return annotations.filterIsInstance(annotationClass)[0]
    }
}

data class ApiMethod(val method: Method, val args: (Array<Any?>)?) {
    val returnType: Type = method.genericReturnType
    val params: List<MethodParam> = method.parameterAnnotations.mapIndexed { index, annotations ->  MethodParam(annotations.toList(), (args!![index] as String?)?: "") }
    val annotations: List<Annotation> = method.annotations.toList()

    init {
        println(returnType)
        require(returnType is ParameterizedType) { "method ${method.name} not return Observable" }
    }


    fun getHeaders(): List<MethodParam> {
        return params.filter { it.hasAnnotation(HEADER::class.java) }
    }

    fun getQuery(): List<MethodParam> {
        return params.filter { it.hasAnnotation(QUERY::class.java) }
    }

    fun getField(): List<MethodParam> {
        return params.filter { it.hasAnnotation(FIELD::class.java) }
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


    fun getPath(): String {
        return when(val method = getMethod()) {
            is GET -> method.path
            is POST -> method.path
            else -> throw IllegalStateException("Can and only have one request method. ")
        }
    }

    fun parseReturnRawType(): Type {
        return if (returnType is ParameterizedType) {
                returnType.rawType
        } else {
            returnType
        }
    }

    fun buildRequest(rootUrl: String): Request {
        val query  = getQuery().map { it.annotation(QUERY::class.java).key to it.value }.toMap()
        val headers = mutableMapOf<String, String>()
        getHeaders().associateTo(headers) { it.annotation(HEADER::class.java).key to it.value }
        val url = packageGetParam(rootUrl + getPath(), query)

        Log.i(TAG, "the url: $url")
        var refer = ""

        try {
            val aURL = URL(url)
            refer = aURL.protocol + "://" + aURL.host
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
        headers["referer"] = refer
        headers["user-agent"] = "Mozilla/5.0 (Linux; Android 5.0; SM-G900P Build/LRX21T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Mobile Safari/537.36"


        val rb = Request.Builder()
                .url(url)
                .headers(Headers.of(headers))


        if (isPost()) {
            val postBuilder = FormBody.Builder()
            this.getField().forEach {
                postBuilder.add(it.annotation(FIELD::class.java).key, it.value)
            }
            rb.post(postBuilder.build())
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

}

/**
 * 接口请求类
 * 可以通过构造函数传入cookieJar来管理cookie。默认没有cookie
 *
 * 可以设置TAG。来改变输出的tag
 * Created by yi on 2/22/16.
 */
class Thyi(val baseUrl: String, var okClient: OkHttpClient? = null) {
    init {
        if (okClient == null) {
            okClient = OkHttpClient.Builder()
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build()
        }
    }

    fun <T>create(define: Class<T>): T {
        return Proxy.newProxyInstance(define.classLoader, arrayOf(define)) { _: Any, method: Method, args: (Array<Any?>)? ->
            if (method.declaringClass == Object::class.java) {
                method.invoke(this, args)
            } else {
                // do the method
                doProxyRequest(method, args)
            }
        } as T
    }

    private fun doProxyRequest(method: Method, args: (Array<Any?>)?): Any? {
        return doRequest(ApiMethod(method, args))
    }

    private fun doRequest(requestOption: ApiMethod): Observable<Any> {
        val returnRawType = requestOption.parseReturnRawType()
        val innerType = (requestOption.returnType as ParameterizedType).actualTypeArguments[0]
        require(returnRawType == Observable::class.java) { "can only return Observable" }
        val request = requestOption.buildRequest(this.baseUrl)

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
        subscribeOn = subscribeOn.observeOn(AndroidSchedulers.mainThread())
        return subscribeOn

    }

    private val JSON = MediaType.parse("application/json; charset=utf-8")
    private lateinit var options: ThyiOptions

    companion object {
        val TAG = "thyi"
    }

}

