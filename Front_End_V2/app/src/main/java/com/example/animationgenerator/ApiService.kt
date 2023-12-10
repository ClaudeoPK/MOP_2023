package com.example.animationgenerator

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path

interface ApiService {
    @Multipart
    @POST("make_ticket")
    suspend fun postImageAndVideo(
        @Part multipartFileList: List<MultipartBody.Part>?
    ): Response<ResponseBody>

    @Multipart
    @POST("get_animation")
    suspend fun getAnimation(
        @PartMap ticket: HashMap<String, RequestBody>
    ): Response<ResponseBody>
}