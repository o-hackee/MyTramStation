package com.example.mytramstation.monitor

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.lang.Exception

private const val BASE_URL = "http://www.wienerlinien.at/ogd_realtime/"

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .build()
private val retrofit = Retrofit.Builder()
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .baseUrl(BASE_URL)
    .build()


data class wlMonitorData(
    val data: wlData,
    val message: wlMessage
)

data class wlMessage(
    val value: String,
    val messageCode: String,
    val serverTime: String // TODO datetime?
)

data class wlData(
    val monitors: List<wlMonitor>,
    // TODO protestit'
    val trafficInfoCategoryGroups: List<wlTrafficInfoCategoryGroup>?,
    val trafficInfoCategories: List<wlTrafficInfoCategory>?,
    val trafficInfos: List<wlTrafficInfo>?
)

data class wlMonitor(
    val locationStop: wlLocationStop,
    val lines: List<wlLine>?,
    val refTrafficInfoNames: String?,
    // TODO потестить бы: непонятно относительное расположение
    val attributes: Any // @note not described in docu
)

data class wlLocationStop(
    val type: String,
    val geometry: wlGeometry,
    val properties: wlLocationProperties
)

data class wlGeometry(
    val type: String,
    val coordinates: List<Double>
)

data class wlLocationProperties(
    val name: String,
    val title: String,
    val municipality: String,
    val municipalityId: Int,
    val type: String,
    val coordName: String,
    val gate: String?,
    val attributes: wlLocationAttributes
)

data class wlLocationAttributes(
    val rbl: Int
)

data class wlLine(
    val name: String,
    val towards: String,
    val direction: String,
    val platform: String,
    val richtungsId: String,
    val barrierFree: Boolean?,
    val realtimeSupported: Boolean?,
    val trafficjam: Boolean?,
    val departures: wlDepartures,
    val type: String,
    val lineId: Int?
)

data class wlDepartures(
    val departure: List<wlDeparture>?
)

data class wlDeparture(
    val departureTime: wlDepartureTime,
    val vehicle: wlVehicle?
)

data class wlDepartureTime(
    val timePlanned: String, // TODO datetime?
    val timeReal: String?,
    val countdown: Int
)

data class wlVehicle(
    val name: String,
    val towards: String,
    val direction: String,
    val richtungsId: String,
    val barrierFree: Boolean,
    val foldingRamp: Boolean?,
    val realtimeSupported: Boolean,
    val trafficjam: Boolean,
    val type: String,
    val attributes: Any, // @note not described in docu
    val linienId: Int
)

// TODO потестить бы
data class wlTrafficInfoCategoryGroup(
    val id: Int,
    val name: String
)
data class wlTrafficInfoCategory(
    val id: Int,
    val refTrafficInfoCategoryGroupId: Int,
    val name: String,
    val trafficInfoNameList: String,
    val title: String
)
data class wlTrafficInfo(
    val name: String,
    val priority: String?,
    val owner: String?,
    val title: String,
    val description: String,
    val relatedLines: String?,
    val relatedStops: String?,
    val time: wlTrafficInfoTime?,
    val attributes: wlTrafficInfoAttributes?
)
data class wlTrafficInfoTime(
    val start: String?, // TODO datetime?
    val end: String?,
    val resume: String?
)
data class wlTrafficInfoAttributes(
    val status: String?,
    val station: String?,
    val location: String?,
    val reason: String?,
    val towards: String?,
    val relatedLines: String?,
    val relatedStops: String?
)

// TODO other request parameters
interface MonitorService {
    @GET("monitor")
    fun getProperties(@Query("stopId") stopId: Int): Call<wlMonitorData>
}

object Monitor {
    private val retrofitService: MonitorService by lazy {
        retrofit.create(MonitorService::class.java)
    }

    enum class MonitorIntentType {
        Tram,
        Bus
    }

    enum class StopLocation(
        val id: Int
    ) {
        Inzersdorf2Oper(5939),
        Inzersdorf2Baden(5903),
        WillendorferGasse(1890), // 65A - Reumann, 66A - Liesing, 67B - Alterlaa
        PurkytgasseBilla(1914); // 65A - Inz, 66A - Reumann, 67B - Alauda
        // PurkytgasseKinsky(1936) // 65A ----\ as Willendorf,...
        // PurkytgasseFar(1966) // 66A, 67B --/ ...but it's closer

        companion object {
            fun from(intentType: MonitorIntentType, slotValue: String): StopLocation {
                return when (intentType) {
                    MonitorIntentType.Tram -> if (slotValue.startsWith("oper")) Inzersdorf2Oper else Inzersdorf2Baden
                    MonitorIntentType.Bus -> if (slotValue.startsWith("will")) WillendorferGasse else PurkytgasseBilla
                }
            }
        }
    }

    fun getDepartures(stopLocation: StopLocation): Int {
        val request = Monitor.retrofitService
        val call = request.getProperties(stopLocation.id)
        return try {
            val response = call.execute()
            // TODO several monitors for buses - one per line
            response.body()?.data?.monitors?.first()?.lines?.first()?.departures?.departure?.first()?.departureTime?.countdown
                ?: -1
        } catch (_: Exception) {
            -2
        }
    }
}