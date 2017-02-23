package org.jenkinsci.plugins.androidsigning;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PretendSlave;

import java.util.List;
import java.util.stream.Collectors;

import hudson.EnvVars;
import hudson.model.Run;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.junit.Assert.assertThat;


public class SignApksStepTest {

    private JenkinsRule testJenkins = new JenkinsRule();
    private TestKeyStore testKeyStore = new TestKeyStore(testJenkins);

    @Rule
    public RuleChain jenkinsChain = RuleChain.outerRule(testJenkins).around(testKeyStore);

    private PretendSlave slave;

    @Before
    public void addSlaveWithZipalignLauncher() throws Exception {
        EnvVars env = new EnvVars();
        env.put("ANDROID_HOME", "");
        slave = testJenkins.createPretendSlave(new FakeZipalign());
        slave.setLabelString(slave.getLabelString() + " " + getClass().getSimpleName());
    }

    @Test
    public void dslWorks() throws Exception {
        // job setup
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(String.format(
            "node {%n" +
            "  wrap($class: 'CopyTestWorkspace') {%n" +
            "    signAndroidApks(" +
            "      keyStoreId: '%s',%n" +
            "      keyAlias: '%s',%n" +
            "      apksToSign: '**/*-unsigned.apk',%n" +
            "      archiveSignedApks: true,%n" +
            "      archiveUnsignedApks: true,%n" +
            "      androidHome: env.ANDROID_HOME%n" +
            "    )%n" +
            "  }%n" +
            "}", TestKeyStore.KEY_STORE_ID, TestKeyStore.KEY_ALIAS)));

        WorkflowRun build = testJenkins.buildAndAssertSuccess(job);
        List<String> artifactNames = build.getArtifacts().stream().map(Run.Artifact::getFileName).collect(Collectors.toList());

        assertThat(artifactNames.size(), equalTo(2));
        assertThat(artifactNames, hasItem(endsWith("SignApksBuilderTest-unsigned.apk")));
        assertThat(artifactNames, hasItem(endsWith("SignApksBuilderTest-signed.apk")));
    }
        
}
