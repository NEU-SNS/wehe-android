//package mobi.meddle.wehe.data.service
//
//import retrofit2.http.FieldMap
//import retrofit2.http.FormUrlEncoded
//import retrofit2.http.GET
//import retrofit2.http.POST
//import retrofit2.http.QueryMap
//import retrofit2.http.Url
//
///**
// * Retrofit service interface for API endpoints
// */
//interface WeheApiService {
//    @GET
//    suspend fun getRequest(@Url url: String, @QueryMap params: Map<String, String>): Map<String, Any>
//
//    @FormUrlEncoded
//    @POST
//    suspend fun postRequest(@Url url: String, @FieldMap params: Map<String, String>): Map<String, Any>
//
//    @GET("WHATSMYIPMAN")
//    suspend fun getPublicIp(): String
//}