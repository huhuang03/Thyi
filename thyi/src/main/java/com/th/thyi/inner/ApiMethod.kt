package com.th.thyi.inner
//
//import android.util.Log
//import com.th.thyi.Thyi
//import com.th.thyi.annotation.*
//import okhttp3.FormBody
//import okhttp3.Headers
//import okhttp3.Request
//import java.lang.IllegalStateException
//import java.lang.reflect.Method
//import java.lang.reflect.ParameterizedType
//import java.lang.reflect.Type
//import java.net.URL
//
///**
// * api method为
// */
//data class ApiMethod(val method: Method, val args: (Array<Any?>)?) {
//    val returnType: Type = method.genericReturnType
//    val params: List<MethodParam> = method.parameterAnnotations.mapIndexed { index, annotations ->
//        MethodParam(annotations.toList(), (args!![index] as String?)
//                ?: "")
//    }
//    val annotations: List<Annotation> = method.annotations.toList()
//
//    init {
//        println(returnType)
//        require(returnType is ParameterizedType) { "method ${method.name} not return Observable" }
//    }
//
//
//    fun getHeaders(): List<MethodParam> {
//        return params.filter { it.hasAnnotation(HEADER::class.java) }
//    }
//
//    fun getQuery(): List<MethodParam> {
//        return params.filter { it.hasAnnotation(QUERY::class.java) }
//    }
//
//    fun getField(): List<MethodParam> {
//        return params.filter { it.hasAnnotation(FIELD::class.java) }
//    }
//
//    fun getMethod(): Annotation {
//        val method = annotations.filter { it is POST || it is GET }
//        check(method.size == 1) { "Can and only have one request method. " }
//        return method[0]
//    }
//
//    fun isGet(): Boolean {
//        return getMethod() is GET
//    }
//
//    fun isPost(): Boolean {
//        return getMethod() is POST
//    }
//
//
//    fun getPath(): String {
//        return when(val method = getMethod()) {
//            is GET -> method.path
//            is POST -> method.path
//            else -> throw IllegalStateException("Can and only have one request method. ")
//        }
//    }
//
//    fun parseReturnRawType(): Type {
//        return if (returnType is ParameterizedType) {
//            returnType.rawType
//        } else {
//            returnType
//        }
//    }
//
//    fun buildRequest(rootUrl: String): Request {
//        val query  = getQuery().map { it.annotation(QUERY::class.java).key to it.value }.toMap()
//        val headers = mutableMapOf<String, String>()
//        getHeaders().associateTo(headers) { it.annotation(HEADER::class.java).key to it.value }
//        val url = packageGetParam(rootUrl + getPath(), query)
//
//        Log.i(Thyi.TAG, "the url: $url")
//        var refer = ""
//
//        try {
//            val aURL = URL(url)
//            refer = aURL.protocol + "://" + aURL.host
//        } catch (ex: Exception) {
//            ex.printStackTrace()
//        }
//        headers["referer"] = refer
//        headers["user-agent"] = "Mozilla/5.0 (Linux; Android 5.0; SM-G900P Build/LRX21T) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/60.0.3112.113 Mobile Safari/537.36"
//
//
//        val rb = Request.Builder()
//                .url(url)
//                .headers(Headers.of(headers))
//
//
//        if (isPost()) {
//            val postBuilder = FormBody.Builder()
//            this.getField().forEach {
//                postBuilder.add(it.annotation(FIELD::class.java).key, it.value)
//            }
//            rb.post(postBuilder.build())
//        }
//
//        return rb.build()
//    }
//
//    private fun packageGetParam(url: String, params: Map<String, String>?): String {
//        if (params == null || params.isEmpty()) return url
//
//        val sb = StringBuilder(url)
//        if (!url.contains("?")) {
//            sb.append("?")
//        }
//
//        return sb.toString() + params.map { "${it.key}=${it.value}" }.joinToString("&")
//    }
//
//}
//
