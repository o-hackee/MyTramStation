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

import com.example.mytramstation.MyTramStationStreamHandler.Companion.skillName
import com.example.mytramstation.MyTramStationStreamHandler.Companion.skillNamePronounce

import com.example.mytramstation.monitor.MonitorWorker


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


class CancelandStopIntentHandler : RequestHandler {
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

// TODO peredelat' na IntentRequestHandler ostal'nye 3?

class NextTramDeparturesIntentHandler: IntentRequestHandler {
    override fun canHandle(input: HandlerInput, intentRequest: IntentRequest): Boolean {
        return input.matches(intentName("nextTramDepartures"))
    }

    override fun handle(input: HandlerInput, intentRequest: IntentRequest): Optional<Response> {
        val slotValue = intentRequest.intent.slots["tramDirection"]?.value ?: ""
        val directiveText = "getting departures in direction of $slotValue"
        val sendDirectiveRequest = SendDirectiveRequest.builder()
            .withHeader(Header.builder().withRequestId(intentRequest.requestId).build())
            .withDirective(SpeakDirective.builder().withSpeech(directiveText).build())
            .build()
        input.serviceClientFactory.directiveService.enqueue(sendDirectiveRequest) // TODO sdelat' async??

        val minutes = MonitorWorker.getDepartures(slotValue.startsWith("oper"))
        val speechText = if (minutes < 0) "Failed to execute a request"
        else "Next departure is in $minutes minutes"
        return input.responseBuilder
            .withSpeech(speechText)
            .withSimpleCard(skillName, speechText)
            .build()
    }
}