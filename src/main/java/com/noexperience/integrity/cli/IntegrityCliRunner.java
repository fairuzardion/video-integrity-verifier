package com.noexperience.integrity.cli;

import com.noexperience.integrity.chain.Manifest;
import com.noexperience.integrity.chain.PemKeys;
import com.noexperience.integrity.chain.SignatureAlgorithm;
import com.noexperience.integrity.chain.SignedManifest;
import com.noexperience.integrity.common.IntegrityException;
import com.noexperience.integrity.common.InvalidInputException;
import com.noexperience.integrity.config.IntegrityProperties;
import com.noexperience.integrity.fingerprint.Sha256;
import com.noexperience.integrity.segmenter.SegmentationParams;
import com.noexperience.integrity.service.RegistrationService;
import com.noexperience.integrity.storage.JsonCodec;
import com.noexperience.integrity.verifier.ManifestVerifier;
import com.noexperience.integrity.verifier.VerificationReport;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs the {@code sign}, {@code verify} and {@code keygen} commands without a web server (profile {@code cli}).
 *
 * <p>Exit codes: 0 AUTHENTIC / success, 1 MODIFIED_OR_REENCODED, 2 TAMPERED, 3 INVALID_SIGNATURE,
 * 64 usage error, 70 other failure.
 */
@Component
@Profile("cli")
public class IntegrityCliRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(IntegrityCliRunner.class);

    static final int EXIT_USAGE = 64;
    static final int EXIT_FAILURE = 70;

    private final RegistrationService registration;
    private final ManifestVerifier verifier;
    private final JsonCodec codec;
    private final IntegrityProperties properties;

    private final PrintStream out = System.out;
    private final PrintStream err = System.err;
    private volatile int exitCode = 0;

    public IntegrityCliRunner(RegistrationService registration, ManifestVerifier verifier, JsonCodec codec,
                              IntegrityProperties properties) {
        this.registration = registration;
        this.verifier = verifier;
        this.codec = codec;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            CliArguments cli = CliArguments.parse(args.getSourceArgs());
            exitCode = switch (cli.command()) {
                case "sign" -> sign(cli);
                case "verify" -> verify(cli);
                case "keygen" -> keygen(cli);
                default -> help();
            };
        } catch (InvalidInputException e) {
            err.println("error: " + e.getMessage());
            err.println("Run with the 'help' command for usage.");
            exitCode = EXIT_USAGE;
        } catch (IntegrityException e) {
            err.println("error: " + e.getMessage());
            exitCode = EXIT_FAILURE;
        } catch (RuntimeException e) {
            log.error("Unexpected failure", e);
            err.println("unexpected error: " + e);
            exitCode = EXIT_FAILURE;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    // ---------------------------------------------------------------------------------------------- sign

    private int sign(CliArguments cli) {
        cli.rejectUnknown(Set.of("input", "output", "name", "segment-seconds", "sample-fps", "analysis-width"));
        Path input = Path.of(cli.required("input"));
        IntegrityProperties.Segmentation defaults = properties.segmentation();
        SegmentationParams params = new SegmentationParams(
                cli.optionalDouble("segment-seconds").orElse(defaults.segmentSeconds()),
                cli.optionalDouble("sample-fps").orElse(defaults.sampleFps()),
                cli.optionalInt("analysis-width").orElse(defaults.analysisWidth()));
        String name = cli.optional("name").orElse(String.valueOf(input.getFileName()));
        Path output = cli.optional("output").map(Path::of)
                .orElse(input.toAbsolutePath().resolveSibling(input.getFileName() + ".manifest.json"));

        SignedManifest signed = registration.register(input, name, params);
        write(output, codec.toJson(signed));

        Manifest m = signed.manifest();
        out.println("Signed manifest written to " + output);
        out.println("  manifest id : " + m.manifestId());
        out.println("  video       : " + m.videoName() + " (" + m.durationSec() + "s, sha256 " + m.videoSha256() + ")");
        out.println("  segments    : " + m.segments().size() + " x " + m.segmentDurationSec() + "s, "
                + m.sampleFps() + " fps, width " + m.analysisWidth());
        out.println("  signature   : " + m.signatureAlgorithm() + ", key " + m.keyId());
        out.println("  chain root  : " + m.chainRootHash());
        return 0;
    }

    // ---------------------------------------------------------------------------------------------- verify

    private int verify(CliArguments cli) {
        cli.rejectUnknown(Set.of("input", "manifest", "report", "json"));
        Path input = Path.of(cli.required("input"));
        Path manifestPath = Path.of(cli.required("manifest"));
        SignedManifest signed = codec.parseManifest(read(manifestPath));

        VerificationReport report = verifier.verify(signed, input, String.valueOf(input.getFileName()));
        String json = codec.toJson(report);
        cli.optional("report").map(Path::of).ifPresent(path -> {
            write(path, json);
            err.println("Report written to " + path);
        });
        out.println(cli.flag("json") ? json : ReportFormatter.format(report));
        return switch (report.verdict()) {
            case AUTHENTIC -> 0;
            case MODIFIED_OR_REENCODED -> 1;
            case TAMPERED -> 2;
            case INVALID_SIGNATURE -> 3;
        };
    }

    // ---------------------------------------------------------------------------------------------- keygen

    private int keygen(CliArguments cli) {
        cli.rejectUnknown(Set.of("private", "public", "algorithm", "force"));
        IntegrityProperties.Keys keys = properties.keys();
        SignatureAlgorithm algorithm = cli.optional("algorithm").map(SignatureAlgorithm::parse).orElse(keys.algorithm());
        Path privatePath = Path.of(cli.optional("private").orElse(keys.privateKeyPath()));
        Path publicPath = Path.of(cli.optional("public").orElse(keys.publicKeyPath()));
        if (!cli.flag("force") && (Files.exists(privatePath) || Files.exists(publicPath))) {
            throw new InvalidInputException("Refusing to overwrite existing key files (" + privatePath + ", "
                    + publicPath + "); pass --force to replace them");
        }
        KeyPair pair = PemKeys.generateKeyPair(algorithm);
        PemKeys.writePrivateKey(privatePath, pair.getPrivate());
        PemKeys.writePublicKey(publicPath, pair.getPublic());
        out.println("Generated " + algorithm.label() + " key pair");
        out.println("  private key : " + privatePath + " (keep secret)");
        out.println("  public key  : " + publicPath + " (distribute to verifiers)");
        out.println("  key id      : " + Sha256.hashHex(pair.getPublic().getEncoded()).substring(0, 16));
        return 0;
    }

    // ---------------------------------------------------------------------------------------------- help / io

    private int help() {
        out.println("""
                video-integrity-verifier CLI

                Usage (the command must be the first argument):
                  sign   --input <video> [--output <manifest.json>] [--name <label>]
                         [--segment-seconds <s>] [--sample-fps <f>] [--analysis-width <px>]
                  verify --input <video> --manifest <manifest.json> [--report <report.json>] [--json]
                  keygen [--algorithm ed25519|ecdsa-p256] [--private <pem>] [--public <pem>] [--force]
                  help

                Spring/application settings can be overridden with --key=value, e.g.
                  --integrity.ffmpeg.binary=/usr/local/bin/ffmpeg --integrity.keys.private-key-path=/secure/key.pem

                Exit codes: 0 AUTHENTIC/ok, 1 MODIFIED_OR_REENCODED, 2 TAMPERED, 3 INVALID_SIGNATURE,
                            64 usage error, 70 failure""");
        return 0;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidInputException("Cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    private static void write(Path path, String content) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new InvalidInputException("Cannot write " + path + ": " + e.getMessage(), e);
        }
    }
}
