package com.codelens.service;

import com.codelens.ingest.ScannedFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangeSetTest {

    @Test
    void classifiesAddedChangedAndDeleted() {
        var stored = Map.of("A.java", "a1", "B.java", "b1", "C.java", "c1");
        var scanned = List.of(file("A.java", "a1"), file("B.java", "b2"), file("D.java", "d1"));

        var changes = ChangeSet.diff(stored, scanned);

        assertThat(changes.added()).containsExactly("D.java");
        assertThat(changes.changed()).containsExactly("B.java");
        assertThat(changes.deleted()).containsExactly("C.java");
        assertThat(changes.isEmpty()).isFalse();
    }

    @Test
    void identicalTreeHasNoChanges() {
        assertThat(ChangeSet.diff(Map.of("A.java", "a"), List.of(file("A.java", "a"))).isEmpty()).isTrue();
    }

    private static ScannedFile file(String path, String sha) {
        return new ScannedFile(path, "root", sha, 1, false);
    }
}
