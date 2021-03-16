package com.example.mytramstation

import com.amazon.ask.dispatcher.request.handler.HandlerInput
import com.amazon.ask.dispatcher.request.handler.RequestHandler
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler
import com.amazon.ask.model.*
import com.amazon.ask.model.services.directive.Header
import com.amazon.ask.model.services.directive.SendDirectiveRequest
import com.amazon.ask.model.services.directive.SpeakDirective
import com.amazon.ask.request.Predicates.intentName
import com.amazon.ask.request.Predicates.requestType

import java.util.Optional
import kotlin.concurrent.thread

import com.example.mytramstation.MyTramStationStreamHandler.Companion.skillName
import com.example.mytramstation.MyTramStationStreamHandler.Companion.skillNamePronounce
import com.example.mytramstation.MyTramStationStreamHandler.Companion.tramDirectionSlotName
import com.example.mytramstation.MyTramStationStreamHandler.Companion.busStopSlotName
import com.example.mytramstation.monitor.Monitor
import java.lang.Exception


const val prompt = "You can ask departures in the specified direction"

class LaunchRequestHandler : RequestHandler {
    override fun canHandle(input: HandlerInput): Boolean {
        return input.matches(requestType(LaunchRequest::class.java))
    }

    override fun handle(input: HandlerInput): Optional<Response> {
        val speechText = "Hello from $skillNamePronounce"
        return input.responseBuilder
            .withSpeech(speechText)
            .withSimpleCard(skillName, speechText)
            .withReprompt(prompt)
            .build()
    }
}


class SessionEndedRequestHandler : RequestHandler {
    override fun canHandle(input: HandlerInput): Boolean {
        return input.matches(requestType(SessionEndedRequest::class.java))
    }

    override fun handle(input: HandlerInput): Optional<Response> {
        // any cleanup logic goes here
        return input.responseBuilder.build()
    }
}


class HelpIntentHandler : RequestHandler {
    override fun canHandle(input: HandlerInput): Boolean {
        return input.matches(intentName("AMAZON.HelpIntent"))
    }

    override fun handle(input: HandlerInput): Optional<Response> {
        val speechText = prompt
        return input.responseBuilder
            .withSpeech(speechText)
            .withSimpleCard(skillName, speechText)
            .build()
    }
}


class FallbackIntentHandler : RequestHandler {
    override fun canHandle(input: HandlerInput): Boolean {
        return input.matches(intentName("AMAZON.FallbackIntent"))
    }

    override fun handle(input: HandlerInput): Optional<Response> {
        val speechText = "Sorry, I don't know that. You can try saying help!"
        return input.responseBuilder
            .withSpeech(speechText)
            .withSimpleCard(skillName, speechText)
            .build()
    }
}


class CancelAndStopIntentHandler : RequestHandler {
    override fun canHandle(input: HandlerInput): Boolean {
        return input.matches(intentName("AMAZON.StopIntent").or(intentName("AMAZON.CancelIntent")))
    }

    override fun handle(input: HandlerInput): Optional<Response> {
        val speechText = "Hear you later"
        return input.responseBuilder
            .withSpeech(speechText)
            .withSimpleCard(skillName, speechText)
            .build()
    }
}

fun formatMinutes(minutes: Int) = "$minutes minute${if (minutes == 1) "" else "s"}"
fun nextDeparturesOneLineString(minutes: List<Int>): String {
    if (minutes.isEmpty())
        return "No departures found"
    return minutes.joinToString(prefix = "Next departures are in ") { formatMinutes(it) }
}

fun nextDeparturesSeveralLinesString(minutesAndLines: List<Pair<Int, String>>): String {
    if (minutesAndLines.isEmpty())
        return "No departures found"
    return minutesAndLines.joinToString(prefix = "Next departures are in ") { "${formatMinutes(it.first)} ${it.second}" }
}
fun handleMonitorIntent(
    input: HandlerInput,
    intentRequest: IntentRequest,
    intentType: Monitor.MonitorIntentType
): Optional<Response> {
    val slotValue = intentRequest.intent.slots[
            if (intentType == Monitor.MonitorIntentType.MyTram) tramDirectionSlotName else busStopSlotName
    ]?.value ?: ""

    val progressiveResponseThread = thread {
        val directiveText =
            "getting departures ${if (intentType == Monitor.MonitorIntentType.MyTram) "in the direction of" else "from"} $slotValue"
        val sendDirectiveRequest = SendDirectiveRequest.builder()
            .withHeader(Header.builder().withRequestId(intentRequest.requestId).build())
            .withDirective(SpeakDirective.builder().withSpeech(directiveText).build())
            .build()
        input.serviceClientFactory.directiveService.enqueue(sendDirectiveRequest)
    }// TODO почему не работает?

    val speechText = try {
        if (intentType == Monitor.MonitorIntentType.MyTram) {
            val minutes = Monitor.getDeparturesOneLine(Monitor.StopLocation.from(intentType, slotValue), 15, 2)
            nextDeparturesOneLineString(minutes)
        }
        else {
            val minutesAndLines = Monitor.getDeparturesSeveralLines(Monitor.StopLocation.from(intentType, slotValue), 15, 2)
            nextDeparturesSeveralLinesString(minutesAndLines)
        }
    }
    catch (e: Exception) {
        "Failed to execute a request with an exception ${e.message}"
    }
    val response = input.responseBuilder
        .withSpeech(speechText)
        .withSimpleCard(skillName, speechText)
        .build()
    progressiveResponseThread.join()
    return response
}

class NextTramDeparturesIntentHandler : IntentRequestHandler {
    override fun canHandle(input: HandlerInput, intentRequest: IntentRequest): Boolean {
        return input.matches(intentName("nextTramDepartures"))
    }

    override fun handle(input: HandlerInput, intentRequest: IntentRequest): Optional<Response> {
        return handleMonitorIntent(input, intentRequest, Monitor.MonitorIntentType.MyTram)
    }
}

class BusesIntentHandler : IntentRequestHandler {
    override fun canHandle(input: HandlerInput, intentRequest: IntentRequest): Boolean {
        return input.matches(intentName("buses"))
    }

    override fun handle(input: HandlerInput, intentRequest: IntentRequest): Optional<Response> {
        return handleMonitorIntent(input, intentRequest, Monitor.MonitorIntentType.MyBuses)
    }

}