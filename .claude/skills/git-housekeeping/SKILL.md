---
name: git-housekeeping
description: Branches, worktrees, stashes, merges and disk cleanup for OmniTuner. Use whenever the user asks what branch they're on or what's uncommitted, wants a new branch, a merge into master or a push, wants worktrees or old branches deleted, is low on disk space, asks to clean up or recover what Gemini (in Android Studio) or another agent changed, or wants changes discarded or brought back. Use it even for a one-line "delete them" or "clean up the mess". Not for writing the commit message itself (AGENTS.md covers style).
---

# Git housekeeping

The user works solo on `master`, often with several Claude sessions and Gemini in Android Studio touching the same checkout. Most mistakes here can't be undone, so this skill is about checking what is there before changing it.

## Before any change: take stock

```bash
git status --short && git branch --show-current && git worktree list && git stash list
```

Report it in plain words (branch, uncommitted files grouped by feature, worktrees, stashes) before acting when the user asked "where are we" or "clean up".

## Deleting worktrees, branches, stashes or files

1. **Scope is exactly what the user named.** "Delete the worktrees" means this project's `.claude/worktrees/`. Anything outside `C:\Projects\OmniTuner-Android-ver` (Agrokz, the web app at `C:\Projects\OmniTuner`) is off limits until the user says so, even if it is the real cause of a full disk. Say what you found there and ask.
2. For each candidate, gather: path, size, branch, uncommitted changes, commits not on `master`, and whether a session is using it (`mcp__ccd_session_mgmt__list_sessions`, look at each `cwd`).
3. Show that as a table and ask which to remove. Any worktree with a live session or uncommitted work is marked and kept by default. "No unsaved work" doesn't make deleting safe: the user may still be working in it.
4. Remove only the approved ones. For app-created worktrees prefer `mcp__ccd_host__clean_up_worktrees` / `discard_kept_worktree`. Otherwise run `./gradlew.bat --stop` first (a running daemon locks `build/` and `.gradle/`), then `git worktree remove <path>`.
5. If Windows stops a removal partway ("Invalid argument", path too long), the folder is half deleted and git has already unregistered it. Report that and ask before forcing it (`cmd /c rmdir /s /q "\\?\C:\…"`).

For disk space, offer the cheap safe win first: `build/` and `.gradle/` inside worktrees can be regenerated. Source can't.

## Discarding changes (Gemini's or anyone's)

Never `git checkout -- .`, `git reset --hard` or `git clean` straight away. Stash with a name so it can come back:

```bash
git stash push -u -m "gemini <what it was> <date>"
```

The user has asked for discarded Gemini work back before. If a stash was already dropped, `git fsck --unreachable | grep commit` and `git show <sha> --stat` find it. Drop a stash only when the user says to, and name which one.

## Branches and merging

- Feature work starts on a new branch (`git switch -c <short-name>`) unless the user said to work on the current one. If you find yourself on `master` with a feature request, say so in one line and branch. "We should've created another branch" has come up before.
- Merge into `master` with a normal merge (`git merge --no-ff <branch>` or fast-forward when possible). The user doesn't want rebasing or a linear history.
- Run the `verify-build` skill on `master` after a merge, before pushing.
- Push only when the user says "push". Committing, merging and pushing are three separate permissions.

## Gotchas

- `.idea/` churn (`.idea/inspectionProfiles/`, `misc.xml`) is never part of a commit. Stage files by name, never `git add -A` or `git add .`.
- Shell-typed commit messages stay ASCII (see `AGENTS.md`).
- `.claude/worktrees/` lives inside the repo. It must stay out of commits, so keep it in `.gitignore`.
- Gemini in Android Studio edits the main checkout directly and never commits. Uncommitted changes nobody in this session made are probably Gemini's, or another session's. Ask before folding them into a commit.
- A black screen right after someone else's changes is usually the emulator snapshot, not the code (see the `verify-build` skill). Check that before reverting anything.
