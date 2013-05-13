package net.sf.buildbox.maven.contentcheck;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import net.sf.buildbox.maven.contentcheck.introspection.DefaultIntrospector;

import org.apache.maven.plugin.logging.Log;

/**
 * The content checker implementation. 
 * <br />
 * Thread safe implementation.
 */
public class ContentChecker {

    private final Log log;
    private final boolean ignoreVendorArchives;
    private final String vendorId;
    private final String manifestVendorEntry;
    private final String checkFilesPattern;

    public ContentChecker(Log log, boolean ignoreVendorArchives, String vendorId, String manifestVendorEntry, String checkFilesPattern) {
        super();
        this.log = log;
        this.ignoreVendorArchives = ignoreVendorArchives;
        this.vendorId = vendorId;
        this.manifestVendorEntry = manifestVendorEntry;
        this.checkFilesPattern = checkFilesPattern;
    }

    /**
     * Checks a content of {@code sourceFile} according to an allowed content defined by {@code listingFile}.
     * 
     * @param listingFile a file that defines allowed content
     * @param sourceFile directory or archive file to be checked
     * 
     * @return the result of source check
     * 
     * @throws IOException if something very bad happen
     */
    public CheckerOutput check(final File listingFile, final File sourceFile) throws IOException{
        final Set<String> allowedEntries = readListing(listingFile);
        DefaultIntrospector introspector = new DefaultIntrospector(log, ignoreVendorArchives, vendorId, manifestVendorEntry, checkFilesPattern);
        int count = introspector.readEntries(sourceFile);
        //XXX dagi: duplicit entries detection https://github.com/buildbox/contentcheck-maven-plugin/issues#issue/4
        final Set<String> entries = introspector.getEntries();
        log.info(String.format("'%s' contains %d checked and %d total files", sourceFile, entries.size(), count));
        return new CheckerOutput(allowedEntries, entries);
    }

    protected Set<String> readListing(final File listingFile) throws IOException {
        log.info("Reading listing: " + listingFile);
        final Set<String> expectedPaths = new LinkedHashSet<String>();
        final BufferedReader reader = new BufferedReader(new FileReader(listingFile));
        try {
            int totalCnt = 0;
            String line;
            while ((line = reader.readLine())!= null) {
                totalCnt ++;
                line = line.trim();
                boolean ignoreLine = line.length() == 0 || line.startsWith("#");// we ignore empty and comments lines
                if (!ignoreLine) { 
                    if(expectedPaths.contains(line)) {
                        log.warn("The listing file " + listingFile + "  defines duplicate entry " + line);
                    }
                    expectedPaths.add(line);
                } 
            }
            log.info(String.format("Content listing file '%s' contains %d paths on %d total lines", listingFile, expectedPaths.size(), totalCnt));
            return expectedPaths;
        } finally {
            reader.close();
        }
    }
}