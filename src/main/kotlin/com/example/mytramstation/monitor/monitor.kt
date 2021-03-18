package com.example.mytramstation.monitor

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.lang.Exception
import java.util.Date
import java.util.concurrent.TimeUnit

private const val BASE_URL = "http://www.wienerlinien.at/ogd_realtime/"

// https://www.data.gv.at/katalog/dataset/522d3045-0b37-48d0-b868-57c99726b1c4
// https://www.wienerlinien.at/ogd_realtime/doku/ogd/wienerlinien-echtzeitdaten-dokumentation.pdf
// https://www.wienerlinien.at/ogd_realtime/monitor?stopId=1914

private val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())
    .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
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
    val serverTime: Date
)

data class wlData(
    val monitors: List<wlMonitor>,
    // TODO протестить
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
    val timePlanned: Date,
    val timeReal: Date?,
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
    val start: Date?,
    val end: Date?,
    val resume: Date?
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

interface MonitorService {
    @GET("monitor")
    fun getProperties(@Query("stopId") stopId: Int): Call<wlMonitorData>
}

object Monitor {
    private val logger: Logger = LoggerFactory.getLogger(Monitor::class.java)

    private val retrofitService: MonitorService by lazy {
        retrofit.create(MonitorService::class.java)
    }

    enum class MonitorIntentType {
        MyTram,
        MyBuses
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
                    MonitorIntentType.MyTram -> if (slotValue.startsWith("oper")) Inzersdorf2Oper else Inzersdorf2Baden
                    MonitorIntentType.MyBuses -> if (slotValue.startsWith("will")) WillendorferGasse else PurkytgasseBilla
                }
            }
        }
    }

    data class TramDeparture(
        val countdown: Int,
        val isLate: Boolean
    )

    data class BusDeparture(
        val countdown: Int,
        val lineName: String,
        val towards: String,
        val isLate: Boolean
    )

    private fun wlDepartureTime.isLate(): Boolean = if (this.timeReal == null)
        false
    else
        this.timeReal.time - this.timePlanned.time > TimeUnit.SECONDS.toMillis(90)

    private fun logTrafficInfo(data: wlData) {
        data.trafficInfoCategoryGroups?.forEach { logger.info(it.toString()) }
        data.trafficInfoCategories?.forEach { logger.info(it.toString()) }
        data.trafficInfos?.forEach { logger.info(it.toString()) }
    }

    fun getDeparturesOneLine(stopLocation: StopLocation, intervalMinutes: Int, numberAtLeast: Int): List<TramDeparture> {
        val call = retrofitService.getProperties(stopLocation.id)
        val responseBody = call.execute().body() ?: throw Exception("Failed to get response")
        logTrafficInfo(responseBody.data)
        val departures = responseBody.data.monitors.firstOrNull()?.lines?.firstOrNull()?.departures?.departure
            ?: throw Exception("No departure data in response")
        var stillToTake = numberAtLeast
        return departures.takeWhile {
            val toTake = it.departureTime.countdown <= intervalMinutes || stillToTake > 0
            if (toTake)
                --stillToTake
            toTake
        }.map { TramDeparture(it.departureTime.countdown,
            it.departureTime.isLate()
        ) }
    }

    fun getDeparturesSeveralLines(stopLocation: StopLocation, intervalMinutes: Int, numberAtLeast: Int): List<BusDeparture> {
        val call = retrofitService.getProperties(stopLocation.id)
        val responseBody = call.execute().body() ?: throw Exception("Failed to get response")
        logTrafficInfo(responseBody.data)
        val lines = responseBody.data.monitors.flatMap { it.lines ?: listOf() }
        val departuresWithLines = lines.flatMap { outerIt -> outerIt.departures.departure?.map { Pair(it, outerIt) } ?: listOf() }
            .sortedBy { it.first.departureTime.countdown }
        var stillToTake = numberAtLeast
        return departuresWithLines.takeWhile {
            val toTake = it.first.departureTime.countdown <= intervalMinutes || stillToTake > 0
            if (toTake)
                --stillToTake
            toTake
        }.map { BusDeparture(
            it.first.departureTime.countdown,
            it.second.name,
            it.second.towards,
            it.first.departureTime.isLate()) }
    }
}
