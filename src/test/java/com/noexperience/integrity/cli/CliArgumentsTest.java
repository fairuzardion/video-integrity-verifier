package com.noexperience.integrity.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.noexperience.integrity.common.InvalidInputException;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CliArgumentsTest {

    @Test
    void detectsCliMode() {
        assertTrue(CliArguments.isCliInvocation(new String[] {"sign", "--input", "a.mp4"}));
        assertFalse(CliArguments.isCliInvocation(new String[] {"--server.port=9090"}));
        assertFalse(CliArguments.isCliInvocation(new String[] {}));
    }

    @Test
    void parsesBothOptionStylesAndIgnoresSpringProperties() {
        CliArguments cli = CliArguments.parse(new String[] {
                "verify", "--input", "a.mp4", "--manifest=m.json", "--json", "--integrity.ffmpeg.binary=/x/ffmpeg"});
        assertEquals("verify", cli.command());
        assertEquals("a.mp4", cli.required("input"));
        assertEquals("m.json", cli.required("manifest"));
        assertTrue(cli.flag("json"));
        cli.rejectUnknown(Set.of("input", "manifest", "json"));
    }

    @Test
    void reportsProblems() {
        CliArguments cli = CliArguments.parse(new String[] {"sign", "--segment-seconds", "abc"});
        assertThrows(InvalidInputException.class, () -> cli.required("input"));
        assertThrows(InvalidInputException.class, () -> cli.optionalDouble("segment-seconds"));
        assertThrows(InvalidInputException.class, () -> cli.rejectUnknown(Set.of("input")));
        assertThrows(InvalidInputException.class, () -> CliArguments.parse(new String[] {"sign", "stray"}));
    }
}
