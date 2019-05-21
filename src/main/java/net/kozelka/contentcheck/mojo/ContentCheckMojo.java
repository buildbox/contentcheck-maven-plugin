package net.kozelka.contentcheck.mojo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import net.kozelka.contentcheck.expect.api.ApproverReport;
import net.kozelka.contentcheck.expect.impl.ContentChecker;
import net.kozelka.contentcheck.expect.impl.ContentCollector;
import net.kozelka.contentcheck.expect.impl.VendorFilter;
import net.kozelka.contentcheck.expect.model.ActualEntry;
import net.kozelka.contentcheck.expect.model.ApprovedEntry;
import net.kozelka.contentcheck.expect.util.ExpectUtils;
import net.kozelka.contentcheck.introspection.ContentIntrospector;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Checks the specified source file (archive or directory, typically project artifact) according to an authoritative
 * source. This authoritative source defines set of allowed files in the source.
 *
 * @since 1.0.0
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class ContentCheckMojo extends AbstractArchiveContentMojo {

    /**
     * If true, no check is performed.
     */
    @Parameter(defaultValue = "false", property = "contentcheck.skip")
    boolean skip;

    /**
     * The file with list of approved files. If such file does not exist, the check is skipped. This enables multimodule use.
     * Each line in represents one pathname entry.
     * Empty lines and comments (starting with '#') are ignored.
     */
    @Parameter(defaultValue = "${basedir}/approved-content.txt")
    File contentListing;

    /**
     * Where to generate content listing. Can be used to fix or initiate the <code>approved-content.txt</code> file.
     */
    @Parameter(defaultValue = "${project.build.directory}/contentcheck-maven-plugin/approved-content.txt")
    File contentListingGenerated;

    /**
     * Message used to report missing entry - uses the {@link java.util.Formatter} syntax to embed entry name.
     */
    @Parameter(defaultValue = "File is expected but not found: %s")
    String msgMissing;

    /**
     * Message used to report unexpected entry - uses the {@link java.util.Formatter} syntax to embed entry name.
     */
    @Parameter(defaultValue = "Found unexpected file: %s")
    String msgUnexpected;

    /**
     * If true, stops the build when there is any file missing.
     */
    @Parameter(defaultValue = "false")
    boolean failOnMissing;

    /**
     * If true, stops the build when there is any unexpected file.
     */
    @Parameter(defaultValue = "true")
    boolean failOnUnexpected;

    public void execute() throws MojoExecutionException, MojoFailureException {

        if (skip) {
            getLog().info("Content checking is skipped.");
            return;
        }

        assertSourceFileExists();

        try {
            final ContentIntrospector introspector = VendorFilter.createIntrospector(new MyIntrospectionListener(getLog()),
                ignoreVendorArchives, vendorId, manifestVendorEntry, checkFilesPattern);
            introspector.setSourceFile(sourceFile);
            //
            if (contentListing.exists()) {
                checkExpectedContent(introspector);
            } else {
                getLog().error(String.format("File '%s' does not exist. Use the generated one (below) as your initial version.", contentListing));
                final Collection<ActualEntry> actualEntries = new ArrayList<ActualEntry>();
                final ContentIntrospector.Events collector = new ContentCollector(actualEntries);
                introspector.getEvents().addListener(collector);
                introspector.walk();
                generate(actualEntries);
            }
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void checkExpectedContent(ContentIntrospector introspector) throws IOException, MojoFailureException {
        final ContentChecker contentChecker = new ContentChecker();
        contentChecker.getEvents().addListener(new MyContentCheckerListener(getLog()));
        contentChecker.setIntrospector(introspector);

        getLog().info("Reading listing: " + contentListing);
        final ApproverReport report = contentChecker.check(contentListing);
        generate(report.getActualEntries());

        // report missing entries
        final Set<ApprovedEntry> missingEntries = report.getMissingEntries();
        for (ApprovedEntry missing : missingEntries) {
            log(failOnMissing, String.format(msgMissing, missing));
        }
        // report unexpected entries
        final Set<ActualEntry> unexpectedEntries = report.getUnexpectedEntries();
        for (ActualEntry actualEntry : unexpectedEntries) {
            log(failOnUnexpected, String.format(msgUnexpected, actualEntry.getUri()));
        }
        // error summary
        if (missingEntries.size() > 0) {
            log(failOnMissing, "Missing: " + missingEntries.size() + " entries");
        }
        if (unexpectedEntries.size() > 0) {
            log(failOnUnexpected, "Unexpected: " + unexpectedEntries.size() + " entries");
        }
        // fail as necessary, after reporting all detected problems
        if (failOnMissing && ! missingEntries.isEmpty()) {
            throw new MojoFailureException(missingEntries.size() + " expected entries are missing in " + sourceFile);
        }

        if (failOnUnexpected && ! unexpectedEntries.isEmpty()) {
            throw new MojoFailureException(unexpectedEntries.size() + " unexpected entries appear in " + sourceFile);
        }

        getLog().info("Source " + sourceFile.getAbsolutePath() + " has valid content according to " + contentListing.getAbsolutePath());
    }

    private void generate(Collection<ActualEntry> actualEntries) throws IOException {
        getLog().info(String.format("Generating content listing from %d existing entries to %s",
            actualEntries.size(),
            contentListingGenerated));
        ExpectUtils.generateListing(actualEntries, contentListingGenerated);
    }

    private void log(boolean error, String message) {
        if (error) {
            getLog().error(message);
        } else {
            getLog().warn(message);
        }
    }
}
