package org.jenkinsci.plugins.androidsigning

import hudson.model.FreeStyleProject
import hudson.model.Items
import hudson.model.listeners.ItemListener
import hudson.tasks.Builder
import hudson.tasks.Shell
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import org.jvnet.hudson.test.WithoutJenkins
import org.mockito.Mockito

import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.assertThat

class MultiEntryToSingleEntryBuilderMigrationTest {

    @Rule
    public JenkinsRule testJenkins = new JenkinsRule();

    @Test
    @WithoutJenkins
    void builderIsNotMigratedIfItHasEntries() throws Exception {
        InputStream oldConfigIn = this.class.getResourceAsStream("compatibility/config-2.0.8.xml")
        FreeStyleProject job = Items.XSTREAM.fromXML(oldConfigIn)

        assertThat(job.buildersList.size(), equalTo(2))

        SignApksBuilder builder = job.buildersList[0]

        assertThat(builder.entries.size(), equalTo(3))
        assertThat(builder.isMigrated(), is(false))

        builder = job.buildersList[1]

        assertThat(builder.entries.size(), equalTo(2))
        assertThat(builder.isMigrated(), is(false))

        builder.entries.clear()

        assertThat(builder.isMigrated(), is(true))
    }

    @Test
    @WithoutJenkins
    void builderIsMigratedWhenEntriesIsNull() throws Exception {
        SignApksBuilder builder = new SignApksBuilder()

        assertThat(builder.entries, nullValue())
        assertThat(builder.isMigrated(), is(true))
    }

    @Test
    void migratesOldData() throws Exception {
        MultiEntryToSingleEntryBuilderMigration migration = ItemListener.all().find { it instanceof MultiEntryToSingleEntryBuilderMigration } as MultiEntryToSingleEntryBuilderMigration
        InputStream configIn = this.class.getResourceAsStream("compatibility/config-2.0.8.xml")
        FreeStyleProject job = Mockito.spy(Items.XSTREAM.fromXML(configIn)) as FreeStyleProject
        testJenkins.jenkins.add(job, this.class.simpleName)
        job.onLoad(testJenkins.jenkins, this.class.simpleName)

        assertThat(job.buildersList.size(), equalTo(2))

        int oldEntryCount = job.buildersList.sum { SignApksBuilder builder -> builder.entries.size() } as int

        migration.onLoaded()

        Mockito.verify(job).save()
        assertThat(job.buildersList.size(), equalTo(oldEntryCount))
    }

    @Test
    void doesNotMigrateAlreadyMigratedData() throws Exception {
        MultiEntryToSingleEntryBuilderMigration migration = ItemListener.all().find { it instanceof MultiEntryToSingleEntryBuilderMigration } as MultiEntryToSingleEntryBuilderMigration
        InputStream configIn = this.class.getResourceAsStream("compatibility/config-2.1.0.xml")
        FreeStyleProject job = Mockito.spy(Items.XSTREAM.fromXML(configIn)) as FreeStyleProject
        testJenkins.jenkins.add(job, this.class.simpleName)
        job.onLoad(testJenkins.jenkins, this.class.simpleName)

        List<Builder> loadedBuilders = new ArrayList<>(job.builders)
        loadedBuilders.each { assertThat(((SignApksBuilder) it).migrated, is(true))}

        migration.onLoaded()

        Mockito.verify(job, Mockito.never()).save()
        assertThat(job.builders.size(), equalTo(loadedBuilders.size()))
        job.builders.eachWithIndex { Builder entry, int i ->
            assertThat(entry, sameInstance(loadedBuilders[i]))
        }
    }

    @Test
    void leavesOtherBuildStepsInPlace() throws Exception {
        MultiEntryToSingleEntryBuilderMigration migration = ItemListener.all().find { it instanceof MultiEntryToSingleEntryBuilderMigration } as MultiEntryToSingleEntryBuilderMigration
        InputStream configIn = this.class.getResourceAsStream("compatibility/config-2.0.8.xml")
        FreeStyleProject job = Items.XSTREAM.fromXML(configIn) as FreeStyleProject
        testJenkins.jenkins.add(job, this.class.simpleName)
        job.onLoad(testJenkins.jenkins, this.class.simpleName)

        assertThat(job.buildersList.size(), equalTo(2))

        int oldEntryCount = job.buildersList.sum { SignApksBuilder builder -> builder.entries.size() } as int

        List<Builder> buildersMod = new ArrayList<>(job.buildersList)
        buildersMod.add(1, new Shell("echo \"${this.class}\""))
        job.buildersList.replaceBy(buildersMod)

        migration.onLoaded()

        assertThat(job.buildersList.size(), equalTo(oldEntryCount + 1))
        assertThat(job.buildersList[0], instanceOf(SignApksBuilder))
        assertThat(job.buildersList[1], instanceOf(SignApksBuilder))
        assertThat(job.buildersList[2], instanceOf(SignApksBuilder))
        assertThat(job.buildersList[3], instanceOf(Shell))
        assertThat(job.buildersList[4], instanceOf(SignApksBuilder))
        assertThat(job.buildersList[5], instanceOf(SignApksBuilder))
    }

}
