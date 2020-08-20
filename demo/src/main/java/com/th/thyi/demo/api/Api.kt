package com.th.thyi.demo.api

import com.th.thyi.annotation.GET
import io.reactivex.rxjava3.core.Observable

interface BaiduApi {
    @GET
    fun home(): Observable<String>
}