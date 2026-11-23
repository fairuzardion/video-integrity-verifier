package com.noexperience.integrity.cli;

import com.noexperience.integrity.common.InvalidInputException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Minimal parser for {@code <command> --key value | --key=value | --flag}. Options containing a dot
 * (e.g. {@code --integrity.ffmpeg.binary=/usr/bin/ffmpeg}) are Spring property overrides and are ignored here.
 */
public final class CliArguments {

    public static final Set<String> COMMANDS = Set.of("sign", "verify", "keygen", "help");

    private final String command;
    private final Map<String, String> options;

    private CliArguments(String command, Map<String, String> options) {
        this.command = command;
        this.options = options;
    }

    /** CLI mode is selected when the very first program argument is a known command. */
    public static boolean isCliInvocation(String[] args) {
        return args != null && args.length > 0 && COMMANDS.contains(args[0]);
    }

    public static CliArguments parse(String[] args) {
        if (!isCliInvocation(args)) {
            throw new InvalidInputException("Expected a command as first argument: " + String.join(", ", COMMANDS));
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (!token.startsWith("--") || token.length() == 2) {
                throw new InvalidInputException("Unexpected argument '" + token + "' (options look like --name value)");
            }
            String body = token.substring(2);
            String key;
            String value;
            int eq = body.indexOf('=');
            if (eq >= 0) {
                key = body.substring(0, eq);
                value = body.substring(eq + 1);
            } else {
                key = body;
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    value = args[++i];
                } else {
                    value = "true";
                }
            }
            if (key.isEmpty()) {
                throw new InvalidInputException("Malformed option '" + token + "'");
            }
            if (key.contains(".")) {
                continue; // Spring property override
            }
            options.put(key, value);
        }
        return new CliArguments(args[0], options);
    }

    public String command() {
        return command;
    }

    public void rejectUnknown(Set<String> allowed) {
        for (String key : options.keySet()) {
            if (!allowed.contains(key)) {
                throw new InvalidInputException("Unknown option --" + key + " for '" + command
                        + "'. Allowed: " + String.join(", ", allowed.stream().sorted().map(s -> "--" + s).toList()));
            }
        }
    }

    public String required(String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Missing required option --" + key);
        }
        return value;
    }

    public Optional<String> optional(String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public OptionalDouble optionalDouble(String key) {
        Optional<String> value = optional(key);
        if (value.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(value.get()));
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Option --" + key + " must be a number, got '" + value.get() + "'");
        }
    }

    public OptionalInt optionalInt(String key) {
        Optional<String> value = optional(key);
        if (value.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(value.get()));
        } catch (NumberFormatException e) {
            throw new InvalidInputException("Option --" + key + " must be an integer, got '" + value.get() + "'");
        }
    }

    public boolean flag(String key) {
        String value = options.get(key);
        return value != null && !value.equalsIgnoreCase("false");
    }
}
