package org.jenkinsci.plugins.androidsigning;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Arrays;

import static org.junit.Assert.fail;

/**
 * Created by stjohnr on 2/14/17.
 */
public class SignApksStepTest {

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    @Test
    public void loadsTheJob() throws Exception {
        // job setup
        WorkflowJob job = testJenkins.jenkins.createProject(WorkflowJob.class, getClass().getSimpleName());
        job.setDefinition(new CpsFlowDefinition(StringUtils.join(Arrays.asList(
                "node {",
                "  echo 'hi'",
                "}"), "\n")));

        WorkflowRun build = testJenkins.assertBuildStatusSuccess(job.scheduleBuild2(0).get());

        fail("add assertions");
    }
        
}
