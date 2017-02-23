package org.jenkinsci.plugins.androidsigning;

import hudson.model.FreeStyleBuild;
import hudson.model.Run;


class BuildArtifact {
    final FreeStyleBuild build;
    final Run.Artifact artifact;

    BuildArtifact(FreeStyleBuild build, Run.Artifact artifact) {
        this.build = build;
        this.artifact = artifact;
    }
}
