package ru.citeck.ecos.commands.test

import ecos.com.fasterxml.jackson210.databind.node.NullNode
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.*
import ru.citeck.ecos.commands.annotation.CommandType
import java.lang.RuntimeException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalCommandTest {

    companion object {
        private const val ADD_NEW_ELEM_TYPE = "add_new_element"
        private const val EX_TEST_ELEM = "EX_TEST"
        private const val EX_TEST_MSG = "EX_TEST TEST MSG"
    }

    val elements = ArrayList<String>()

    @Test
    fun test() {

        val commandsServiceFactory = CommandsServiceFactory()
        val commandsService = commandsServiceFactory.commandsService

        commandsService.addExecutor(AddElementExecutor())

        elements.clear()

        val testElem = "test-elem"
        val command = AddElementCommand(testElem)

        val result = commandsService.executeSync(command)
        val resultObj = result.getResultData(CommandAddResult::class.java)

        assertEquals(testElem, resultObj!!.value)
        assertTrue(result.errors.isEmpty())
        assertEquals(1, elements.size)
        assertEquals(testElem, elements[0])

        val commandFromResult = result.getCommandData(AddElementCommand::class.java)
        assertEquals(commandFromResult, command)

        val exRes = commandsService.executeSync(AddElementCommand(EX_TEST_ELEM))

        assertEquals(1, exRes.errors.size)
        assertEquals(EX_TEST_MSG, exRes.errors[0].message)
        assertEquals("RuntimeException", exRes.errors[0].type)
        assertEquals(NullNode.getInstance(), exRes.result)
    }

    inner class AddElementExecutor : CommandExecutor<AddElementCommand> {

        override fun execute(command: AddElementCommand) : Any {
            if (command.element == EX_TEST_ELEM) {
                throw RuntimeException(EX_TEST_MSG)
            }
            elements.add(command.element)
            return CommandAddResult(command.element)
        }
    }

    data class CommandAddResult(
        val value: String
    )

    @CommandType(ADD_NEW_ELEM_TYPE)
    data class AddElementCommand(
        val element: String
    )
}
