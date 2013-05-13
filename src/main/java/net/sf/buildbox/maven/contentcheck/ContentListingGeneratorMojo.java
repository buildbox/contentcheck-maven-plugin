package net.sf.buildbox.maven.contentcheck;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Set;


import net.sf.buildbox.maven.contentcheck.introspection.DefaultIntrospector;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

/**
 * MOJO for content listing generation from a given source file. This MOJO takes {@link #getSourceFile()}
 * and generates its content to {@link #getContentListing()}.The MOJO generates
 * to the output only entities that matches criteria defined by {@link #getCheckFilesPattern()} 
 * and {@link #isIgnoreVendorArchives()}.
 *
 * @goal generate
 */
public class ContentListingGeneratorMojo extends AbstractArchiveContentMojo {

    /**
     * This parameter allows overwriting existing listing file.
     * @parameter default-value="false"
     */
    private boolean overwriteExistingListing; 

    protected void doExecute() throws IOException, MojoExecutionException, MojoFailureException {
        if(!overwriteExistingListing && getContentListing().exists()) {
            throw new MojoFailureException(String.format("Content listing file '%s' already exists. Please set overwriteExistingListing property in plugin configuration or delete this listing file.", getContentListing().getPath()));
        }

        DefaultIntrospector introspector = new DefaultIntrospector(getLog(), isIgnoreVendorArchives(), getVendorId(), getManifestVendorEntry(), getCheckFilesPattern());
        int count = introspector.readEntries(getSourceFile());
        Set<String> sourceEntries = introspector.getEntries();
        getLog().info(String.format("The source contains entries %d, but only %d matches the plugin configuration criteria.", count, sourceEntries.size()));
        

        FileWriter writer = null;
        try {
            writer = new FileWriter(getContentListing());
            writer.write("# Content listing generated Maven Content Check Plugin (https://github.com/buildbox/contentcheck-maven-plugin)\n");
            writer.write("#\n");
            writer.write(String.format("# At '%s' \n", new Date().toString()));
            writer.write(String.format("# Source '%s'\n", getSourceFile()));
            writer.write(String.format("# Used options: checkFilesPattern='%s' ignoreVendorArchives='%s'\n", getCheckFilesPattern(), Boolean.toString(isIgnoreVendorArchives())));
            writer.write("#\n");
            writer.write("# Feel free to edit this file\n");
            writer.write("\n");
            for (String entryName : sourceEntries) {
                writer.write(entryName);
                writer.write("\n");
        }
        } finally {
            try {
                writer.close();
            } catch(IOException e) {
                //close silently
            }
        }
        getLog().info(String.format("The listing file '%s' has been succesfully generated.", getContentListing()));
    }

}
