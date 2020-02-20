package com.github.yqy7.linereaderloop;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;
import org.jline.builtins.Builtins;
import org.jline.builtins.Completers.SystemCompleter;
import org.jline.builtins.Options.HelpException;
import org.jline.builtins.Widgets.AutosuggestionWidgets;
import org.jline.builtins.Widgets.CmdDesc;
import org.jline.builtins.Widgets.TailTipWidgets;
import org.jline.builtins.Widgets.TailTipWidgets.TipType;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.ParentCommand;
import picocli.shell.jline3.PicocliCommands;

/**
 * @author qiyun.yqy
 * @date 2020/2/20
 */
public class LineReaderLoop {
    private CommandRegistry commandRegistry;
    private Object banner;
    private String prompt;
    private boolean enableTailTip;
    private boolean enableSuggestion;

    private LineReaderLoop() {
    }

    public void run() throws Exception {
        AnsiConsole.systemInstall();
        printBanner();

        Terminal terminal = TerminalBuilder.builder().build();
        LineReader lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(commandRegistry.getCompleter())
            .parser(new DefaultParser())
            .build();

        commandRegistry.setLineReader(lineReader);

        if (enableSuggestion) {
            AutosuggestionWidgets autosuggestionWidgets = new AutosuggestionWidgets(lineReader);
            autosuggestionWidgets.enable();
        }

        if (enableTailTip) {
            TailTipWidgets tailTipWidgets = new TailTipWidgets(lineReader, cmdLine -> {
                switch (cmdLine.getDescriptionType()) {
                    case COMMAND:
                        String cmd = Parser.getCommand(cmdLine.getArgs().get(0));
                        return commandRegistry.commandDescription(cmd);
                    default:
                        break;
                }
                return null;
            }, 5, TipType.COMBINED);
            tailTipWidgets.enable();
        }

        while (true) {
            try {
                String line = lineReader.readLine(prompt);
                if (line.matches("^\\s*$") || line.matches("^\\s*#.*")) {
                    continue;
                }

                ParsedLine parsedLine = lineReader.getParser().parse(line, 0);
                String[] cmdArgs = parsedLine.words().toArray(new String[0]);
                String command = Parser.getCommand(line);
                commandRegistry.execute(command, cmdArgs);
            } catch (HelpException e) {
                HelpException.highlight(e.getMessage(), HelpException.defaultStyle()).print(
                    lineReader.getTerminal());
            } catch (UserInterruptException e) {
                // ignore
            } catch (EndOfFileException e) {
                return;
            } catch (Exception e) {
                printError(e.getMessage(), terminal);
            }
        }
    }

    private void printBanner() {
        Ansi banner = Ansi.ansi()
            .fg(Color.GREEN)
            .a(getBannerString())
            .reset();
        System.out.println(banner);
    }

    private void printError(String msg, Terminal terminal) {
        AttributedStringBuilder asb = new AttributedStringBuilder();
        asb.append(msg, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
        asb.toAttributedString().println(terminal);
    }

    private String getBannerString() {
        try {
            InputStream inputStream;
            if (banner instanceof URL) {
                inputStream = ((URL)banner).openStream();
            } else if (banner instanceof File) {
                inputStream = new FileInputStream((File)banner);
            } else {
                return "";
            }

            return readString(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    private String readString(InputStream inputStream) {
        try (Scanner scanner = new Scanner(inputStream)) {
            StringBuilder stringBuilder = new StringBuilder();
            while (scanner.hasNextLine()) {
                stringBuilder.append(scanner.nextLine());
                if (scanner.hasNextLine()) {
                    stringBuilder.append("\n");
                }
            }
            return stringBuilder.toString();
        }
    }

    // Builder

    public static LineReaderLoopBuilder builder() {
        return new LineReaderLoopBuilder();
    }

    public static class LineReaderLoopBuilder {
        private LineReaderLoop lineReaderLoop = new LineReaderLoop();

        public LineReaderLoopBuilder setCommandRegistry(CommandRegistry commandRegistry) {
            lineReaderLoop.commandRegistry = commandRegistry;
            return this;
        }

        public LineReaderLoopBuilder setBanner(URL url) {
            lineReaderLoop.banner = url;
            return this;
        }

        public LineReaderLoopBuilder setBanner(File file) {
            lineReaderLoop.banner = file;
            return this;
        }

        public LineReaderLoopBuilder setPrompt(String prompt) {
            lineReaderLoop.prompt = prompt;
            return this;
        }

        public LineReaderLoopBuilder setEnableTailTip(boolean enableTailTip) {
            lineReaderLoop.enableTailTip = enableTailTip;
            return this;
        }

        public LineReaderLoopBuilder setEnableSuggestion(boolean enableSuggestion) {
            lineReaderLoop.enableSuggestion = enableSuggestion;
            return this;
        }

        public LineReaderLoop build() {
            return lineReaderLoop;
        }
    }

    @Command(name = "")
    public static class CommandRegistry {
        private static Logger logger = LoggerFactory.getLogger(CommandRegistry.class);

        private List<Class> commandClassList = new ArrayList<>();
        private PicocliCommands picocliCommands;
        private Builtins builtins;
        private LineReader lineReader;
        private Object context;

        private CommandRegistry() {
        }

        public LineReader getLineReader() {
            return lineReader;
        }

        public void setLineReader(LineReader lineReader) {
            if (builtins != null) {
                builtins.setLineReader(lineReader);
            }

            this.lineReader = lineReader;
        }

        public Object getContext() {
            return context;
        }

        private CommandLine createCommandLine() {
            CommandLine commandLine = new CommandLine(this);
            for (Class clazz : commandClassList) {
                try {
                    commandLine.addSubcommand(clazz.newInstance());
                } catch (Exception e) {
                    logger.error("", e);
                }
            }
            return commandLine;
        }

        public Completer getCompleter() {
            SystemCompleter systemCompleter;
            if (builtins != null) {
                systemCompleter = builtins.compileCompleters();
                systemCompleter.add(picocliCommands.compileCompleters());
            } else {
                systemCompleter = picocliCommands.compileCompleters();
            }
            systemCompleter.compile();
            return systemCompleter;
        }

        public boolean hasCommand(String cmd) {
            if (builtins != null) {
                return builtins.hasCommand(cmd) || picocliCommands.hasCommand(cmd);
            } else {
                return picocliCommands.hasCommand(cmd);
            }
        }

        public CmdDesc commandDescription(String cmd) {
            if (builtins != null && builtins.hasCommand(cmd)) {
                return builtins.commandDescription(cmd);
            } else if (picocliCommands.hasCommand(cmd)) {
                return picocliCommands.commandDescription(cmd);
            } else {
                return null;
            }
        }

        public void execute(String command, String[] cmdArgs) throws Exception {
            if (builtins != null && builtins.hasCommand(command)) {
                builtins.execute(command, Arrays.copyOfRange(cmdArgs, 1, cmdArgs.length),
                    System.in, System.out, System.err);
            } else if (picocliCommands.hasCommand(command)) {
                createCommandLine().execute(cmdArgs);
            } else {
                throw new RuntimeException(String.format("Command %s not found!", command));
            }
        }

        // builder

        public static CommandRegistry.CommandRegistryBuilder builder() {
            return new CommandRegistry.CommandRegistryBuilder();
        }

        public static class CommandRegistryBuilder {
            CommandRegistry commandRegistry = new CommandRegistry();

            public CommandRegistry.CommandRegistryBuilder addCommand(Class clazz) {
                commandRegistry.commandClassList.add(clazz);
                return this;
            }

            public CommandRegistry.CommandRegistryBuilder setContext(Object context) {
                commandRegistry.context = context;
                return this;
            }

            public CommandRegistry.CommandRegistryBuilder addBuiltins() {
                commandRegistry.commandClassList.add(BuiltinCmdExit.class);
                commandRegistry.commandClassList.add(BuiltinCmdClear.class);
                commandRegistry.commandClassList.add(HelpCommand.class);
                return this;
            }

            public CommandRegistry.CommandRegistryBuilder addJlineBuiltins() {
                commandRegistry.builtins = new Builtins(Paths.get(""), null, null);
                return this;
            }

            public CommandRegistry build() {
                commandRegistry.picocliCommands = new PicocliCommands(Paths.get(""),
                    commandRegistry.createCommandLine());
                return commandRegistry;
            }
        }
    }

    @Command(name = "exit", aliases = "quit", description = "Exit the application.")
    public static class BuiltinCmdExit implements Callable<Integer> {
        @Override
        public Integer call() throws Exception {
            System.out.println("Bye.");
            System.exit(0);
            return 0;
        }
    }

    @Command(name = "clear", aliases = "cls", description = "Clear the screen.")
    public static class BuiltinCmdClear implements Callable<Integer> {
        @ParentCommand
        CommandRegistry parent;

        @Override
        public Integer call() throws Exception {
            if (parent.getLineReader() instanceof LineReaderImpl) {
                ((LineReaderImpl)parent.getLineReader()).clearScreen();
            }
            return 0;
        }
    }
}