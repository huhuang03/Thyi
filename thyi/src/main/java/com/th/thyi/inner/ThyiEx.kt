package com.th.thyi.inner
//
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.util.Log
//import com.google.gson.Gson
//import io.reactivex.Observable
//import io.reactivex.Observable.create
//import io.reactivex.ObservableOnSubscribe
//import io.reactivex.android.schedulers.AndroidSchedulers
//import io.reactivex.schedulers.Schedulers
//import okhttp3.*
//import org.json.JSONObject
//import java.io.InputStream
//import java.lang.reflect.*
//import java.util.concurrent.TimeUnit
//
//data class MethodParam(val annotations: List<Annotation>, val value: String) {
//    fun hasAnnotation(annotationClass: Class<out Annotation>): Boolean {
//        return annotations.isNotEmpty() && annotations.filterIsInstance(annotationClass).isNotEmpty()
//    }
//
//    /**
//     * get single annotation
//     */
//    fun <T>annotation(annotationClass: Class<T>): T where T: Annotation {
//        return annotations.filterIsInstance(annotationClass)[0]
//    }
//}
//
///**
// * 接口请求类
// * 可以通过构造函数传入cookieJar来管理cookie。默认没有cookie
// *
// * 可以设置TAG。来改变输出的tag
// * Created by yi on 2/22/16.
// */
//class ThyiEx(val baseUrl: String, var okClient: OkHttpClient? = null) {
//    init {
//        if (okClient == null) {
//            okClient = OkHttpClient.Builder()
//                    .readTimeout(20, TimeUnit.SECONDS)
//                    .build()
//        }
//    }
//
//    /**
//     * 通过api的定义，创建api的实现。
//     */
//    fun <T>create(define: Class<T>): T {
//        return Proxy.newProxyInstance(define.classLoader, arrayOf(define)) { _: Any, method: Method, args: (Array<Any?>)? ->
//            if (method.declaringClass == Object::class.java) {
//                method.invoke(this, args)
//            } else {
//                // do the method
//                doProxyRequest(method, args)
//            }
//        } as T
//    }
//
//    private fun doProxyRequest(method: Method, args: (Array<Any?>)?): Any? {
//        return doRequest(ApiMethod(method, args))
//    }
//
//    private fun doRequest(requestOption: ApiMethod): Observable<Any> {
//        val returnRawType = requestOption.parseReturnRawType()
//        val innerType = (requestOption.returnType as ParameterizedType).actualTypeArguments[0]
//        require(returnRawType == Observable::class.java) { "can only return Observable" }
//        val request = requestOption.buildRequest(this.baseUrl)
//
//        val onSubscribe = ObservableOnSubscribe<Any> { e ->
//            try {
//                Log.i(TAG, "send on thread; ${Thread.currentThread().name}")
//                val response = okClient!!.newCall(request).execute()
//                Log.i(TAG, "onResponse")
//
//                val data = when (innerType) {
//                    InputStream::class.java -> {
//                        response.body()!!.byteStream()
//                    }
//                    Response::class.java -> {
//                        response
//                    }
//                    Bitmap::class.java -> {
//                        val inputStream = response.body()!!.byteStream()
//                        BitmapFactory.decodeStream(inputStream)?: Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
//                    }
//                    else -> {
//                        val str = response.body()!!.string()
//                        Log.i(TAG, "rst: " + str + "\n\t for url: " + request.url())
//
//                         when (innerType) {
//                            String::class.java -> str
//                            JSONObject::class.java -> JSONObject(str)
//                            else ->  {
//                                Gson().fromJson(str, innerType)
//                            }
//                        }
//                    }
//                }
//
//                e.onNext(data)
//                e.onComplete()
//            } catch (ex: Exception) {
//                ex.printStackTrace()
//                e.onError(ex)
//            }
//        }
//
//        var subscribeOn = create(onSubscribe).subscribeOn(Schedulers.io())
//        subscribeOn = subscribeOn.observeOn(AndroidSchedulers.mainThread())
//        return subscribeOn
//
//    }
//
//    private val JSON = MediaType.parse("application/json; charset=utf-8")
//    private lateinit var options: ThyiOptions
//
//    companion object {
//        val TAG = "thyi"
//    }
//
//}
//
