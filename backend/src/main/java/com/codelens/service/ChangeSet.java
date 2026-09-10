package com.codelens.service;

import com.codelens.ingest.ScannedFile;

import java.util.*;

// file-level diff by content hash
public record ChangeSet(Set<String> added, Set<String> changed, Set<String> deleted) {

    public static ChangeSet diff(Map<String, String> stored, Collection<ScannedFile> scanned) {
        var added = new TreeSet<String>();
        var changed = new TreeSet<String>();
        var seen = new HashSet<String>();
        for (ScannedFile f : scanned) {
            seen.add(f.path());
            String old = stored.get(f.path());
            if (old == null) added.add(f.path());
            else if (!old.equals(f.sha256())) changed.add(f.path());
        }
        var deleted = new TreeSet<String>();
        for (String path : stored.keySet()) {
            if (!seen.contains(path)) deleted.add(path);
        }
        return new ChangeSet(added, changed, deleted);
    }

    public boolean isEmpty() {
        return added.isEmpty() && changed.isEmpty() && deleted.isEmpty();
    }
}
