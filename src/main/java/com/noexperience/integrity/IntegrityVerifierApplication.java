package com.noexperience.integrity;

import com.noexperience.integrity.cli.CliArguments;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Entry point. Without arguments (or with Spring options only) the REST API starts.
 * When the first argument is {@code sign}, {@code verify}, {@code keygen} or {@code help}, the application runs in
 * CLI mode: no web server is started, the {@code cli} profile is activated and the process exit code reflects the result.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class IntegrityVerifierApplication {

    public static void main(String[] args) {
        boolean cliMode = CliArguments.isCliInvocation(args);
        SpringApplication application = new SpringApplication(IntegrityVerifierApplication.class);
        if (cliMode) {
            application.setWebApplicationType(WebApplicationType.NONE);
            application.setAdditionalProfiles("cli");
            application.setBannerMode(Banner.Mode.OFF);
        }
        ConfigurableApplicationContext context = application.run(args);
        if (cliMode) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
