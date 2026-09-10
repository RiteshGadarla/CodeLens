package com.codelens.ingest;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class GitClient {

    public String cloneRepo(String url, String branch, Path dir) throws Exception {
        var cmd = Git.cloneRepository().setURI(url).setDirectory(dir.toFile()).setCloneAllBranches(false);
        if (!url.startsWith("file:")) cmd.setDepth(1);
        if (branch != null && !branch.isBlank()) cmd.setBranch(branch);
        try (Git git = cmd.call()) {
            return head(git.getRepository());
        }
    }

    // fetch + hard reset to the remote branch
    public String pull(Path dir) throws Exception {
        try (Git git = Git.open(dir.toFile())) {
            String branch = git.getRepository().getBranch();
            var fetch = git.fetch().setRemote("origin")
                    .setRefSpecs(new RefSpec("+refs/heads/" + branch + ":refs/remotes/origin/" + branch));
            String url = git.getRepository().getConfig().getString("remote", "origin", "url");
            if (url != null && !url.startsWith("file:")) fetch.setDepth(1);
            fetch.call();
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("refs/remotes/origin/" + branch).call();
            return head(git.getRepository());
        }
    }

    // null when not inside a git repository
    public String headCommit(Path dir) {
        var builder = new FileRepositoryBuilder().findGitDir(dir.toFile());
        if (builder.getGitDir() == null) return null;
        try (Repository repo = builder.build()) {
            return head(repo);
        } catch (IOException e) {
            return null;
        }
    }

    private static String head(Repository repo) throws IOException {
        ObjectId id = repo.resolve("HEAD");
        return id == null ? null : id.name();
    }
}
