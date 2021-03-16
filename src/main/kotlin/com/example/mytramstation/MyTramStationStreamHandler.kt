package com.example.mytramstation

import com.amazon.ask.Skills
import com.amazon.ask.SkillStreamHandler


class MyTramStationStreamHandler : SkillStreamHandler(
    Skills.standard()
        .addRequestHandlers(
            LaunchRequestHandler(),
            SessionEndedRequestHandler(),
            NextTramDeparturesIntentHandler(),
            BusesIntentHandler(),
            HelpIntentHandler(),
            FallbackIntentHandler(),
            CancelandStopIntentHandler(),
        )
        .withSkillId(skillId)
        .build()
) {
    companion object {
        const val skillId = "amzn1.ask.skill.d037f536-96e8-4289-b6e5-cf5140eb4d56"
        const val skillName = "MyTramStation"
        const val skillNamePronounce = "my tram station"

        const val tramDirectionSlotName = "tramDirection"
        const val busStopSlotName = "busStop"
    }
}