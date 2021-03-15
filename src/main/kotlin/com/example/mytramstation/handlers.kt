package com.example.mytramstation

import com.amazon.ask.dispatcher.request.handler.HandlerInput
import com.amazon.ask.dispatcher.request.handler.RequestHandler
import com.amazon.ask.dispatcher.request.handler.impl.IntentRequestHandler
import com.amazon.ask.model.IntentRequest
import com.amazon.ask.model.LaunchRequest
import com.amazon.ask.model.Response
import com.amazon.ask.model.SessionEndedRequest
import com.amazon.ask.request.Predicates.intentName
import com.amazon.ask.request.Predicates.requestType

import java.util.Optional

import com.example.mytramstation.MyTramStationStreamHandler.Companion.skillName
import com.example.mytramstation.MyTramStationStreamHandler.Companion.skillNamePronounce


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
                .withReprompt(speechText)
                .build()
    }
}


class FallbackIntentHandler : RequestHandler {
    override fun canHandle(input: HandlerInput): Boolean {
        return input.matches(intentName("AMAZON.FallbackIntent"))
    }

    override fun handle(input: HandlerInput): Optional<Response> {
        val speechText = "Sorry, I don't know that. You can say try saying help!"
        return input.responseBuilder
                .withSpeech(speechText)
                .withSimpleCard(skillName, speechText)
                .withReprompt(speechText)
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
        val intent = intentRequest.intent
        val slotValue = intent.slots["tramDirection"]?.value ?: ""

        val minutes = MonitorWorker.getDepartures(slotValue.startsWith("oper"))
        var speechText = "working on it, got slot as $slotValue. "
        speechText += if (minutes < 0) "Failed to execute a request"
        else "Next departure is in $minutes minutes"
        return input.responseBuilder
            .withSpeech(speechText)
            .withSimpleCard(skillName, speechText)
            .withReprompt(speechText)
            .build()
    }
}