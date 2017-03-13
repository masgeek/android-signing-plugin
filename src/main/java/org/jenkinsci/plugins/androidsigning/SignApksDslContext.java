package org.jenkinsci.plugins.androidsigning;


import hudson.Extension;
import javaposse.jobdsl.dsl.Context;
import javaposse.jobdsl.dsl.helpers.step.StepContext;
import javaposse.jobdsl.plugin.ContextExtensionPoint;
import javaposse.jobdsl.plugin.DslExtensionMethod;

@Extension(optional = true)
public class SignApksDslContext extends ContextExtensionPoint {

    public static class ConfigureContext implements Context {

        private final SignApksBuilder builder;

        private ConfigureContext(SignApksBuilder builder) {
            this.builder = builder;
        }

        public void keyStoreId(String x) {
            builder.setKeyStoreId(x);
        }

        public void keyAlias(String x) {
            builder.setKeyAlias(x);
        }

        public void skipZipalign(boolean x) {
            builder.setSkipZipalign(x);
        }

        public void archiveSignedApks(boolean x) {
            builder.setArchiveSignedApks(x);
        }

        public void archiveUnsignedApks(boolean x) {
            builder.setArchiveUnsignedApks(x);
        }

        public void androidHome(String x) {
            builder.setAndroidHome(x);
        }

        public void zipalignPath(String x) {
            builder.setZipalignPath(x);
        }
    }

    @DslExtensionMethod(context = StepContext.class)
    public SignApksBuilder signAndroidApks(String apksToSign, Runnable configClosure) {
        SignApksBuilder builder = new SignApksBuilder();
        builder.setApksToSign(apksToSign);
        executeInContext(configClosure, new ConfigureContext(builder));
        return builder;
    }

}
