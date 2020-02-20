package com.github.yqy7.linereaderloop;

import java.util.concurrent.Callable;

import com.github.yqy7.linereaderloop.LineReaderLoop.CommandRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * @author qiyun.yqy
 * @date 2020/2/20
 */
public class Example {
    public static void main(String[] args) {
        try {
            CommandRegistry commandRegistry = CommandRegistry.builder()
                .addCommand(VultrCmdStatus.class)
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
    public static class VultrCmdStatus implements Callable<Integer> {
        @ParentCommand
        CommandRegistry parent;

        @Override
        public Integer call() throws Exception {
            System.out.println("Display status!!!!!!!");
            return 0;
        }
    }
}
