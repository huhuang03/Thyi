package com.th.thyi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.InputStream
import java.lang.IllegalStateException
import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

class Thyi(var baseUrl: String = "",
           var okhttp: OkHttpClient = OkHttpClient.Builder()
                   .readTimeout(20, TimeUnit.SECONDS)
                   .build()) {

    companion object {
        const val TAG = "thyi"
    }

    fun <T>request(request: Request, classOfT: Class<T>): Observable<T> {
        return request(request, classOfT as Type)
    }

    fun <T>request(request: Request, typeOfT: Type): Observable<T> {
        Log.i(TAG, "send: " + request.url().toString())
        val onSubscribe = ObservableOnSubscribe<T> { e ->
            try {
                val response = okhttp.newCall(request).execute()
                when (typeOfT) {
                    InputStream::class.java -> {
                        e.onNext(response.body()!!.byteStream() as T)
                        e.onComplete()
                    }
                    Response::class.java -> {
                        e.onNext(response as T)
                        e.onComplete()
                    }
                    Bitmap::class.java -> {
                        val inputStream = response.body()!!.byteStream()
                        var bitmap: Bitmap? = BitmapFactory.decodeStream(inputStream)
                        response.close()
                        if (bitmap == null) {
                            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                        }
                        e.onNext(bitmap as T)
                        e.onComplete()
                    }
                    else -> {
                        val str = response.body()!!.string()
                        response.close()
                        Log.i(TAG, "rst: " + str + "\n\t for url: " + request.url())
                        val bean: T

                        bean = when (typeOfT) {
                            String::class.java -> str as T
                            JSONObject::class.java -> JSONObject(str) as T
                            else ->  {
                                if (str == "") {
                                    throw IllegalStateException("request failed: ${request.url()}")
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