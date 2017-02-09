package org.jenkinsci.plugins.androidsigning;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Apk extends AbstractDescribableImpl<Apk> {
    private String keyStore;
    private String alias;
    private String selection;
    private boolean archiveUnsignedApks;
    private boolean archiveSignedApks;

    @DataBoundConstructor
    public Apk(String keystore, String alias, String selection, boolean archiveUnsignedApks, boolean archiveSignedApks) {
        this.keyStore = keystore;
        this.alias = alias;
        this.selection = selection;
        this.archiveUnsignedApks = archiveUnsignedApks;
        this.archiveSignedApks = archiveSignedApks;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Apk> {
        @Override
        public String getDisplayName() {
            return ""; // unused
        }
    }

    public String getSelection() {
        return selection;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public String getAlias() {
        return alias;
    }

    public boolean getArchiveUnsignedApks() {
        return archiveUnsignedApks;
    }

    public boolean getArchiveSignedApks() {
        return archiveSignedApks;
    }

    public String[] getSelectionGlobs() {
        String[] globs = getSelection().split("\\s*,\\s*");
        List<String> cleanGlobs = new ArrayList<>(globs.length);
        for (String glob : globs) {
            glob = glob.trim();
            if (glob.length() > 0) {
                cleanGlobs.add(glob);
            }
        }
        return cleanGlobs.toArray(new String[cleanGlobs.size()]);
    }
}
