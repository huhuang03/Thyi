package com.th.thyi.demo.api

import com.th.thyi.annotation.GET
import io.reactivex.Observable

interface BaiduApi {
    @GET
    fun home(): Observable<String>
}