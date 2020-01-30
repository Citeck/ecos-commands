import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import ru.citeck.ecos.commands.CommandExecutor
import ru.citeck.ecos.commands.CommandsServiceFactory
import kotlin.test.assertEquals

class SimpleTest {

    val ADD_NEW_ELEM_TYPE = "add_new_element"

    val elements = ArrayList<String>()

    @Test
    fun test() {

        val commandsServiceFactory = CommandsServiceFactory()
        val commandsService = commandsServiceFactory.commandsService

        commandsService.addExecutor(AddElementExecutor())

        elements.clear()

        val testElem = "test-elem"

        commandsService.execute(
            type = ADD_NEW_ELEM_TYPE,
            data = AddElementCommand(testElem)
        )

        assertEquals(1, elements.size)
        assertEquals(testElem, elements[0])
    }

    inner class AddElementExecutor : CommandExecutor<AddElementCommand> {

        override fun execute(command: AddElementCommand): String {
            elements.add(command.element)
            return ""
        }

        override fun getType(): String {
            return ADD_NEW_ELEM_TYPE
        }
    }

    data class AddElementCommand(
        val element: String
    )
}
