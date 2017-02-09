package org.jenkinsci.plugins.androidsigning;


import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class ApkTest {

    @Test
    public void splitsMultipleGlobs() {
        Apk apk = new Apk("my.keyStore", "my-alias", "*test1,test2/xyz*/**, test3/** ,", true, true);
        String[] globs = apk.getSelectionGlobs();

        assertThat(globs.length, equalTo(3));
        assertThat(globs[0], equalTo("*test1"));
        assertThat(globs[1], equalTo("test2/xyz*/**"));
        assertThat(globs[2], equalTo("test3/**"));
    }

    @Test
    public void splitsSingleGlob() {
        Apk apk = new Apk("my.keyStore", "my-alias", "*test1", true, true);
        String[] globs = apk.getSelectionGlobs();

        assertThat(globs.length, equalTo(1));
        assertThat(globs[0], equalTo("*test1"));
    }
}
