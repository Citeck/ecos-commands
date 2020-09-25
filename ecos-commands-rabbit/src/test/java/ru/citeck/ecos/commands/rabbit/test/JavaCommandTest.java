package ru.citeck.ecos.commands.rabbit.test;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import ru.citeck.ecos.commands.CommandExecutor;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.CommandsServiceFactory;
import ru.citeck.ecos.commands.annotation.CommandType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaCommandTest {

    private final List<CommandBody> bodyList = new ArrayList<>();

    @Test
    void test() {

        CommandsServiceFactory factory = new CommandsServiceFactory();
        CommandsService commandsService = factory.getCommandsService();
        commandsService.addExecutor(new Executor());

        CommandBody commandBody = new CommandBody("one-two-three", 999);
        commandsService.executeSync(commandBody);

        assertEquals(1, bodyList.size());
        assertEquals(commandBody, bodyList.get(0));
    }

    @CommandType("test-type")
    static class CommandBody {

        private String field0;
        private int field1;

        public CommandBody() {
        }

        public CommandBody(String field0, int field1) {
            this.field0 = field0;
            this.field1 = field1;
        }

        public String getField0() {
            return field0;
        }

        public void setField0(String field0) {
            this.field0 = field0;
        }

        public int getField1() {
            return field1;
        }

        public void setField1(int field1) {
            this.field1 = field1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CommandBody that = (CommandBody) o;
            return field1 == that.field1 &&
                Objects.equals(field0, that.field0);
        }

        @Override
        public int hashCode() {
            return Objects.hash(field0, field1);
        }
    }

    class Executor implements CommandExecutor<CommandBody> {

        @Nullable
        @Override
        public Object execute(CommandBody command) {
            bodyList.add(command);
            return null;
        }
    }
}
