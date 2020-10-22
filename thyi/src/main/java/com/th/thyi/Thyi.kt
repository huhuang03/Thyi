package com.th.thyi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableOnSubscribe
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.lang.IllegalStateException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import kotlin.reflect.typeOf


class Thyi(var okhttp: OkHttpClient = OkHttpClient.Builder()
                   .readTimeout(20, TimeUnit.SECONDS)
                   .build()) {

    companion object {
        const val TAG = "thyi"
    }

    fun <T> post(url: String, form: Map<String, String>, classOfT: Class<T>): Observable<T> {
        return request(Request.Builder()
                .method("POST"
                    , FormBody.Builder().apply {
                        for (item in form) {
                            this.add(item.key, item.value)
                        }
                    }
                .build())
                .url(url).build(), classOfT)
    }

    fun <T>post(url: String, classOfT: Class<T>): Observable<T> {
        return request(Request.Builder()
                .method("POST", FormBody.Builder().build())
                .url(url).build(), classOfT)
    }

    fun <T>postJson(url: String, json: String, classOfT: Class<T>): Observable<T> {
        return request(Request.Builder()
            .method("POST", json.toRequestBody("application/json".toMediaTypeOrNull()))
            .url(url).build(), classOfT)
    }

    fun <T>get(url: String, classOfT: Class<T>): Observable<T> {
        return get(url, emptyMap(), classOfT)
    }

    fun <T> get(url: String, query: Map<String, String>, classOfT: Class<T>): Observable<T> {
        return request(Request.Builder()
            .method("GET", null)
            .url(url.toHttpUrl().newBuilder().apply {
                for (item in query) {
                    addQueryParameter(item.key, item.value)
                }
            }.build().toUrl()).build(), classOfT)
    }


    fun <T>request(request: Request, classOfT: Class<T>): Observable<T> {
        return request(request, classOfT as Type)
    }

    fun <T>request(request: Request, typeOfT: Type): Observable<T> {
        Log.i(TAG, "send: ${request.url.toString()}, method: ${request.method}")
        val onSubscribe = ObservableOnSubscribe<T> { e ->
            try {
                val response = okhttp.newCall(request).execute()
                when (typeOfT) {
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
                        response.close()
                        if (bitmap == null) {
                            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                        }
                        e.onNext(bitmap as T)
                        e.onComplete()
                    }
                    else -> {
                        val str = response.body!!.string()
                        response.close()
                        Log.i(TAG, "rst: " + str + "\n\t for url: " + request.url)
                        val bean: T

                        bean = when (typeOfT) {
                            String::class.java -> str as T
                            JSONObject::class.java -> JSONObject(str) as T
                            else ->  {
                                if (str == "") {
                                    throw IllegalStateException("request failed: ${request.url}")
                                }
                                Gson().fromJson(str, typeOfT)
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
        var subscribeOn = Observable.create(onSubscribe).subscribeOn(Schedulers.io())
        subscribeOn = subscribeOn.observeOn(AndroidSchedulers.mainThread())
        return subscribeOn
    }
}