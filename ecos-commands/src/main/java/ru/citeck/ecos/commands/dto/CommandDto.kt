package ru.citeck.ecos.commands.dto

import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Instant

data class CommandDto(
    val id: String,
    val time: Instant,
    val targetApp: String,
    val actor: String,
    val source: String,
    val type: String,
    val data: ObjectNode = JsonNodeFactory.instance.objectNode()
)

/*{
    "id": "123e4567-e89b-12d3-a456-426655448474",
    "time": "2019-01-01T01:01:01.952Z",
    "target": "eproc",
    "actor": "system",
    "source": "alfresco:a8aae115-e2c5-418c-a261-61ed4ce94ba8",
    "type": "activity.complete",
    "config": {
        "activityId": "2143",
        "processId": "0/cmmn$c7a57bf4-43b8-4c78-a154-7551aac0152d",
        "attributes": {
            "outcome": "Done"
        }
    }
}*/
