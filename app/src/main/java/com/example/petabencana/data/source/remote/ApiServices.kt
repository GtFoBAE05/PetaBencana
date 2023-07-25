package com.example.petabencana.data.source.remote

import com.example.petabencana.data.model.UrunDaya
import retrofit2.http.GET
import retrofit2.http.Query


interface ApiServices{

    @GET("reports")
    suspend fun getLatestReports(
        @Query("disaster") disaster: String?=null
    ) : UrunDaya

}