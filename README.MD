A tool to create interactive shell, using jline and picocli.

```java
import java.util.concurrent.Callable;

import com.github.yqy7.linereaderloop.LineReaderLoop.CommandRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

public class Example {
    public static void main(String[] args) {
        try {
            CommandRegistry commandRegistry = CommandRegistry.builder()
                .addCommand(ExampleCmdStatus.class)
                .addBuiltins()
                .addJlineBuiltins()
                .build();

            LineReaderLoop.builder()
                .setCommandRegistry(commandRegistry)
                .setPrompt("uvultr> ")
                .build()
                .run();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Command(name = "status", description = "Display connection status")
    public static class ExampleCmdStatus implements Callable<Integer> {
        @ParentCommand
        CommandRegistry parent;

        @Override
        public Integer call() throws Exception {
            System.out.println("Display status!!!!!!!");
            return 0;
        }
    }
}
```