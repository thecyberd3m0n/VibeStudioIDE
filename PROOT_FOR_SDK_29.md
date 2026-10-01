# PROOT_FOR_SDK_29 — a rootless, ptrace‑free Linux runtime for `targetSdk ≥ 29`

> **Why this file is named for SDK 29.** Android 10 (API level 29) is the exact
> point where the platform enforces **W^X** on app‑private storage: an app whose
> `targetSdkVersion ≥ 29` may **not** `execve()` or `mmap(PROT_EXEC)` a file it
> wrote into its own data directory, regardless of the file's mode bits. This is
> an **SELinux** rule keyed to the app's target SDK (domain `untrusted_app_29+`),
> **not** a runtime permission — no user consent, `MANAGE_EXTERNAL_STORAGE`, or
> SAF grant re‑enables it. Every writable location a user can hand us
> (`filesDir`, SAF trees, `/sdcard`) is exec‑denied.
>
> `targetSdk ≤ 28` gets legacy semantics and can exec from app storage — which is
> exactly why classic proot/Termux distros work there. But Google Play requires
> **new apps and updates to target a recent API level**, so the SDK‑28 escape
> hatch is closed to us on Play. This runtime is what lets the environment run at
> `targetSdk 34+` **without a VM and without ptrace overhead**.

---

## 1. Goal

A userspace **fakeroot / chroot Linux environment** that, *from the guest's
perspective*, has a fully writable, fully executable, root‑owned filesystem —
while Android's outer sandbox stays fully enforced.

- **Android view:** app‑private files, no exec, uid = app, noexec storage.
- **Guest view:** `/`, `/usr`, `/bin` … owned `root:root`, mode `0755`, the
  x‑bit honored, `uid = 0`, everything runnable.

This is a **userland‑exec engine (reflective ELF loading), not an emulator and
not a VM.** The guest's real arm64 instructions run **directly on the real CPU at
native speed** — nothing simulates a processor. Only two things are "virtual":
the *loading path* (bytes become runnable memory without a kernel `exec`) and the
*view* (fakeroot/chroot over files and credentials). Think **rootless container
with a custom loader**, not QEMU.

Two mechanisms create that illusion:

1. **Execution** — we never ask the kernel to exec a guest file. We **read the
   ELF as data → map anonymous `execmem` → run it.** The x‑bit becomes
   *behavioral*, not filesystem‑real. (`execmem` — anonymous `PROT_EXEC` — **is**
   allowed for `untrusted_app_29+`; ART's own JIT relies on it. This single fact
   is what the whole design rests on. **Validate it on real OEM devices first.**)
2. **Permissions** — a **virtual inode metadata store** overlays
   `stat`/`chmod`/`chown` so the guest sees whatever owner/mode it expects. Full
   permissions inside, zero permissions leaked outside.

Design constraints (inherited from the product thesis — see `README.md`):

- **100% on‑device.** No remote execution, ever.
- **FOSS / clean‑room.** Built from public references (ELF spec, musl/glibc
  `ld.so`, proot). **Never** decompile or copy any third‑party proprietary `.so`.
  The *technique* is unencumbered; the *implementation* must be ours.
- **Fallback for correctness.** Anything the fast path can't handle falls back to
  **proot (GPLv2)** or **qemu‑user (GPL)** so users never see a capability gap.

Scope: **arm64‑v8a**. Production guest is **glibc** (Ubuntu/Debian) — because
prebuilt binaries (node‑gyp, esbuild/swc, pip wheels) target glibc. **But the
first bootstrap target is Alpine / musl**, on purpose: musl's dynamic linker is
small and clean, so it is by far the easiest first subject for reflective
loading. Prove the engine on musl, then graduate to glibc's harder linker.

> **apt is never ditched.** Only the *engine + libc/ld.so* are prebundled (in
> `nativeLibraryDir`). Everything a user or the agent installs — `apt install
> php`, `npm i`, native addons — lands in the writable rootfs and runs through
> the **same reflective loader**. The user and the agent must never see or feel
> the mechanism; from inside it is just Linux.

---

## 2. Architecture at a glance

```
Android app process (Bionic, jniLibs = exec-permitted)
        │  execve()  (only ever the stub — it lives in nativeLibraryDir)
        ▼
  stub_loader  ──►  elf_loader  ──►  guest glibc ld.so (unmodified)
        │                                     ▲
        │                                     │ LD_PRELOAD (glibc-built shim)
        ▼                                     │
  guest process (glibc) ── syscalls ──► syscall_shim
                                           ├─ path_translator  (rootfs illusion)
                                           ├─ fakeroot         (root + mode overlay)
                                           ├─ vfs_shims        (/proc /dev /sys /pts)
                                           └─ exec_router      (re-route child execs)
                          raw syscalls / static bins ─► syscall_trap (seccomp SUD, phase 2)
                          anything unsupported        ─► proot / qemu-user fallback
```

---

## 2B. Reflective ELF loading — the engine ("what actually runs the code")

### What ELF is
**ELF** (Executable and Linkable Format) is the container for executables, shared
objects (`.so`), and object files on Linux. A file has **two parallel views**:

- **Program headers = the *execution* view** (what the loader needs). Each entry
  is a segment:
  - `PT_LOAD` — a chunk to map into memory: `p_offset`, `p_vaddr`, `p_filesz`,
    `p_memsz`, and `p_flags` (R/W/X). Text is `R+X`, data is `R+W`.
  - `PT_INTERP` — path to the dynamic linker (e.g. `/lib/ld-musl-aarch64.so.1`).
  - `PT_DYNAMIC` — dynamic‑link info: `DT_NEEDED` (deps), `DT_RELA`, `DT_SYMTAB`,
    `DT_JMPREL`, etc.
  - `PT_TLS` — thread‑local‑storage template. `PT_GNU_RELRO`, `PT_GNU_STACK`,
    `PT_PHDR` — hardening / self‑reference.
- **Section headers = the *linking* view** (symbols, relocs, debug). **Not needed
  at runtime** — the loader works purely from program headers.

The **ELF header** gives: magic `\x7fELF`, class `ELFCLASS64`, machine
`EM_AARCH64`, type `ET_EXEC` (fixed base) vs `ET_DYN` (PIE / `.so`, relocatable
base), `e_entry` (entry vaddr), `e_phoff`/`e_phnum` (program header table).

### How we call it — the engine API
A single entry point, invoked **inside the `stub_loader`** (never over our own
live image — see caveat):

```c
// Reflective userland-exec: run a guest ELF without kernel execve.
// Returns only on failure; on success it transfers control and never returns.
int  vibe_ulexec(const char *guest_path,     // path in the (translated) rootfs
                 char *const argv[],
                 char *const envp[],
                 const ul_config *cfg);      // rootfs root, load-base policy,
                                             // interp override, verbosity
```

Fast path (`bash` spawns `git`): the libc `execve` shim catches the call and
re‑routes to `vibe_ulexec`. Raw‑syscall execs are caught later by seccomp
user‑notify (§3.9).

### The load sequence (reflective / `ul_exec`)
1. `open`+`read` the ELF as **data** (allowed on app storage).
2. Parse + validate ELF header (`ELFCLASS64`, `EM_AARCH64`).
3. Determine `ET_EXEC` vs `ET_DYN`; pick a **load bias** (PIE ⇒ free base away
   from the loader's own pages; fixed‑base `ET_EXEC` is the awkward case).
4. Reserve the full span with one `mmap(PROT_NONE, MAP_ANONYMOUS)`.
5. For each `PT_LOAD`: `mmap` anon `R+W` over its slice, copy `[p_offset,
   p_offset+p_filesz)`, zero the bss `[p_filesz, p_memsz)`, then `mprotect` to
   `p_flags` — **exec pages are anonymous ⇒ legal.**
6. If `PT_INTERP`: load the dynamic linker (`ld-musl` first, later glibc `ld.so`)
   the **same** way, and hand control to *it* with a correct `auxv` so it links
   the main object. Route its library mmaps to `nativeLibraryDir` copies so they
   stay legal (see §3.2 / §3.11).
7. Build the initial stack: `argc`, `argv[]`, `envp[]`, and **`auxv`**
   (`AT_PHDR`, `AT_PHENT`, `AT_PHNUM`, `AT_ENTRY`, `AT_BASE`=interp base,
   `AT_PAGESZ`, `AT_RANDOM`, `AT_HWCAP`/`AT_HWCAP2`, `AT_SECURE=0`, `AT_EXECFN`,
   `AT_NULL`). The dynamic linker reads `auxv` to find the program.
8. **asm trampoline**: set `sp` to the crafted stack, clear registers per the
   aarch64 SysV entry convention, `br` to the interp entry (or `e_entry+bias`).

### Critical caveat — don't overwrite yourself
A classic `ul_exec` *replaces* the current process image, which would unmap the
code currently executing. We avoid that: the tiny `stub_loader` (in
`nativeLibraryDir`) loads the guest at a **non‑overlapping base** and jumps,
leaving its own small footprint resident and unused. PIE guests (`ET_DYN`, the
common case) make this trivial; fixed‑base `ET_EXEC` that collides with the stub
is the one case needing a relocated stub layout.

### Reference material (clean‑room)
**Primary reference: [`anvilsecure/ulexecve`](https://github.com/anvilsecure/ulexecve) (BSD‑3‑Clause).**
A single‑file userland‑exec that already supports **aarch64** and is tested
against **static, dynamic, PIE, Rust and Go** binaries. It does the exact fiddly
work we need — ELF parse, segment mapping, **auxv/stack construction**, and the
**jump buffer** (trampoline). BSD‑3 means we may **read, adapt, and translate it
to C++ with attribution** — no contamination risk (contrast proroot). It is a
*reference to port*, **not** the shipping engine (it's Python).

Plus **musl's `ldso/dynlink.c`** as the simplest correct linker reference, and
**proot's `src/loader/loader.c`** (GPLv2) for how it wires the **interpreter
hand‑off + path‑translated exec** — it already maps a guest binary *plus* its
`ld.so`, builds auxv, and jumps; the **only** thing making it 29+‑incompatible is
that it uses **file‑backed `mmap(fd, PROT_EXEC)`**. Swap that one pattern for
**read → anon `execmem` → copy** and it becomes 29+‑capable (derivative ⇒ GPLv2,
keep separable). Prefer **ulexecve** (permissive) as the primary port source;
mine `loader.c` for the interpreter/rootfs wiring. Plus the ELF spec. Build from
these; never from a proprietary binary.

> **Two Android caveats ulexecve does NOT cover:**
> 1. Its "100% reliable" `memfd_create()` + `fexecve()` **fallback is unusable**
>    on Android — SELinux blocks executing a memfd for `untrusted_app` at
>    targetSdk ≥ 29. We rely purely on the **anon‑mmap + jump** path; our fallback
>    is **qemu‑user**, not memfd.
> 2. It runs on desktop Linux (no noexec), so it lets the guest `ld.so` load
>    `DT_NEEDED` libs via **file‑backed `PROT_EXEC` mmap** — exactly what Android
>    blocks. ulexecve therefore covers **§3.1 (main binary + interpreter)** but
>    **not §3.2** (making the guest linker's *dependent‑library* mmaps legal on
>    Android). §3.2 stays ours.

---

## 3. C++ modules

### 3.1 `elf_loader` — userspace ELF loader (core)
- **Port target: translate [`anvilsecure/ulexecve`](https://github.com/anvilsecure/ulexecve)
  (BSD‑3, aarch64) to C++** — it already implements the parse/map/auxv/jump‑buffer
  logic below; keep its attribution.
- Parse ELF64/aarch64: `PT_LOAD`, `PT_INTERP`, `PT_DYNAMIC`, `PT_TLS`,
  `PT_GNU_RELRO`, `PT_GNU_STACK`.
- Load each `PT_LOAD`: `open`+`read` the file, `mmap(MAP_ANONYMOUS,
  PROT_READ|WRITE)`, copy bytes, `mprotect` to final perms (add `PROT_EXEC` on the
  **anonymous** mapping — the noexec‑file bypass).
- Build the initial image: stack, `argv`/`envp`/**`auxv`** (`AT_PHDR`, `AT_ENTRY`,
  `AT_BASE`, `AT_RANDOM`, `AT_HWCAP`, `AT_PAGESZ`, `AT_SECURE=0`), TLS.
- Per‑arch **asm trampoline** to set `sp`/registers and jump to entry (or to the
  guest `ld.so`).

### 3.2 `dyn_linker_glue` — make stock glibc `ld.so` work  ⭐ *highest‑risk spike*
A dynamic guest needs `ld-linux-aarch64.so.1`, which itself maps `.so` deps with
`PROT_EXEC` from files → blocked. Preferred approach:
- **Interpose `mmap`/`openat` inside the guest** so every file‑backed `PROT_EXEC`
  map is transparently converted to "read file → anon exec map." One interception
  makes **unmodified glibc `ld.so`** work → IFUNC, TLS, symbol versioning,
  `DT_NEEDED` all come for free.
- Fallback plan if interposition proves impossible: write a **minimal dynamic
  linker** (much more work — IFUNC + TLS models are the pain). Avoid unless forced.

### 3.3 `stub_loader` — kernel‑execable bootstrap (ships in `jniLibs/arm64-v8a/`)
- A **tiny static executable named `lib*.so`** in `nativeLibraryDir` — the only
  exec‑permitted path. The kernel *can* exec this.
- To spawn a guest process: `execve` the stub → it receives the target guest path
  + argv via fd/env → calls `elf_loader` to map the real binary into its own clean
  address space → jumps. Gives every guest process a **pristine image**.
- Must be **static / self‑contained** so it doesn't hit the dependency‑load
  problem it exists to solve.

### 3.4 `exec_router` — exec interception
- Hook `execve`, `execveat`, `posix_spawn`, `fork`/`vfork`+exec, and **shebang
  (`#!`) parsing** + interpreter recursion.
- Reroute every exec through `stub_loader` + `elf_loader` instead of the kernel.
- Correct `fork`/`wait`/exit‑code/signal propagation so `make`, `npm`, etc.
  spawning subprocesses behave.

### 3.5 `fakeroot` — permission & credential emulation  ⭐ *the "full permissions inside"*
- **Credentials:** `getuid/geteuid/getgid/getegid/getgroups` → `0`;
  `setuid/setgid/setgroups` → accept, no‑op success.
- **Virtual inode metadata store:** persistent map
  `rootfs_path/inode → {uid, gid, mode, rdev, xattrs}`.
  - `chmod/chown/fchmodat/fchownat` → **write to store**, return success.
  - `stat/lstat/fstat/fstatat/statx` → **overlay store values** onto the real
    result: report guest‑intended `uid/gid/mode` **including the exec bit**. This
    overlay is what makes files "executable from the Linux perspective."
  - `mknod` → virtual device nodes; setuid/sticky bits, capabilities live here.
- Persist the store (SQLite or an mmap'd flat db) so `/usr` perms survive restarts.

### 3.6 `path_translator` — chroot / rootfs illusion
- Rewrite guest absolute paths (`/usr`, `/etc`, `/bin`) → host rootfs paths for
  **every** path‑taking libc call (`open`, `openat`, `access`, `readlink`,
  `chdir`, `getcwd`, `rename`, `unlink`, `mkdir`, `mount`→no‑op …).
- **Symlink containment** (no `..`/symlink escape from the rootfs), bind‑mount
  table, `link2symlink` hardlink emulation for FSes that reject hardlinks.

### 3.7 `vfs_shims` — /proc, /dev, /sys, PTY
- Synthesize `/proc/self/exe` (→ guest path), `/proc/self/fd`, `/proc/self/maps`,
  `/proc/self/cmdline`, `/proc/cpuinfo`, `/proc/meminfo`, `/proc/mounts`.
- `/dev/null|zero|random|urandom|tty`, and crucially **`/dev/ptmx` + `/dev/pts`**
  wired to an Android‑allocated PTY (needed for the terminal/shell).

### 3.8 `bridge` — Bionic ↔ glibc split (compile‑time discipline)
- **Host launcher + stub** compile with the **NDK (Bionic)**, arm64, static →
  `jniLibs/`.
- **Guest‑side shim** (`dyn_linker_glue` + `fakeroot` + `path_translator` +
  `exec_router` hooks) must be **compiled against glibc** and `LD_PRELOAD`ed into
  the guest — mixing Bionic TLS/`pthread`/`errno` into a glibc process crashes.
- ⇒ **Two artifacts, two toolchains.** This drives the whole build setup.

### 3.9 `syscall_trap` — raw‑syscall interception (redirect *everything* to the engine)
LD_PRELOAD only catches libc calls; **static binaries and raw `svc #0`** (Go,
static musl) bypass it. We don't "relink" a raw syscall (no symbol) — we **trap**
it and dispatch to our engine. Three mechanisms, in priority order:

1. **Syscall User Dispatch (SUD) — primary. ⭐**
   `PR_SET_SYSCALL_USER_DISPATCH` makes the kernel raise **`SIGSYS`** for every
   syscall *except* those from a designated **selector region** (our dispatcher).
   - Guest code (static *or* dynamic, libc *or* raw `svc`) → `SIGSYS` **in
     process** → our handler dispatches:
     - `execve`/`execveat` → **do not forward to kernel; call `vibe_ulexec`.**
       *(this is the "redirect exec to our engine" the whole design needs).*
     - `open`/`stat`/… → path‑translate + fakeroot, then issue the **real**
       syscall from inside the selector region (so it doesn't re‑trap).
   - Native speed on the real CPU; only the syscall *crossing* costs a signal.
     No supervisor process, no CPU emulation. This is how **FEX‑Emu / box64**
     run foreign code on Android, and it **closes the static‑binary gap** —
     LD_PRELOAD becomes an optional fast path, not a correctness requirement.
   - **Requires kernel ≥ 5.11** (Android 5.15+ GKI has it; 5.10/5.4 don't).

2. **seccomp user‑notify — fallback (out‑of‑process).**
   `SECCOMP_RET_USER_NOTIF` on FS/exec/cred syscalls; a supervisor emulates them
   (result written back via `process_vm_writev`/`/proc/pid/mem`), and routes
   `execve` through `vibe_ulexec`. Higher latency; awkward for high‑frequency
   data‑returning syscalls. Use when unotify exists but SUD does not.

3. **`svc` rewriting — optional hot‑path optimizer.**
   Since the guest lives in our own anon memory, scan for `svc #0` and patch each
   into a branch to a trampoline (aarch64 cousin of x86 `zpoline`). Fastest
   steady state, but `b` reaches only ±128 MB and `svc` can hide in data →
   brittle. Reserve for hot paths only.

**Layering:** SUD available → SUD catches everything, LD_PRELOAD is the fast
path. SUD absent → seccomp‑unotify for the FS/exec/cred subset, else
**proot/qemu‑user fallback**. Promote from "phase 2" — SUD is what makes the
engine *complete*.

### 3.10 `runtime` — session / process orchestrator
- Rootfs setup, mount table, env (`PATH`/`HOME`/`TERM`), spawn login shell via
  stub, child/signal/PTY management, stream stdio to the app UI.

---

## 4. Build / NDK notes
- **Two toolchains:** NDK‑Bionic (host launcher + static stub, → `jniLibs/`) and
  glibc‑aarch64 cross (guest shim).
- Ship all kernel‑execable code as `lib*.so` in `nativeLibraryDir`
  (`extractNativeLibs=true`); **everything the guest runs stays data.**
- Per‑arch asm for the loader hand‑off (register/stack setup, jump).
- PIE / position‑independent throughout.

---

## 5. Milestones (build in this order)

### Phase 0 — Prove the foundation
- [ ] **Spike A: `execmem` at `targetSdk 34`.** Minimal app that maps anon
      `PROT_EXEC` memory, writes aarch64 code, runs it. Test on stock + Samsung +
      Xiaomi. **Go/no‑go for the entire approach.**
- [ ] **Spike B: reflective loader on Alpine/musl (first real step).**
      Use an **Alpine arm64 rootfs** (musl). Reflectively load, in order:
      1. a **static** musl binary (`busybox`) — proves `elf_loader` +
         auxv/stack + trampoline (no linker yet).
      2. a **dynamic** musl binary that pulls in `libc.so` — proves the
         `PT_INTERP` hand‑off to `ld-musl` with library mmaps routed to
         `nativeLibraryDir` (retires the hardest §3.2 unknown on the *easy*
         linker).
      3. an **interpreted** workload (`/bin/sh` script, then `python`/`node` if
         present) — proves child `execve` interception + re‑entry into the
         engine.
      Success = an interactive `ash`/`bash` from the Alpine rootfs running native.
- [ ] **Spike C: graduate to glibc `ld.so`** once musl works (§3.2 on the harder
      linker: IFUNC / TLS / symbol versioning).

### Phase 1 — Minimal runnable guest
- [ ] `stub_loader` (3.3) in `jniLibs/`, launches a static binary.
- [ ] `elf_loader` (3.1) full segment/auxv/TLS setup.
- [ ] `path_translator` (3.6) + `fakeroot` (3.5) enough to run `bash` from an
      Ubuntu arm64 rootfs with `uid=0` and a working `stat` overlay.
- [ ] `vfs_shims` PTY so an interactive shell works in the app terminal.

### Phase 2 — Real workloads
- [ ] `exec_router` (3.4): `bash` → spawns `node`, `python`, `git`.
- [ ] Validate: **`npm install` + a Vite build**, `pip install` (pure‑Python),
      `git clone`. These are the "write me a React app" acceptance tests.
- [ ] `fakeroot` metadata persistence across sessions.

### Phase 3 — Close the gaps
- [ ] `syscall_trap` (3.9) seccomp‑SUD for static binaries / raw syscalls (Go).
- [ ] Automatic **fallback to proot / qemu‑user** for anything still unsupported.
- [ ] node‑gyp native addon build; Chromium/Playwright as stretch validation.

---

## 6. Known gaps (what we're missing until matured)

Severity: 🔴 blocks the thesis · 🟠 real limitation, has a path · 🟢 minor / cost.

| # | Gap | Why | Status & mitigation |
|---|---|---|---|
| 1 | 🔴 **`execmem` denied on some OEM/kernel** | vendor SELinux may not grant anon `PROT_EXEC` to `untrusted_app_29+` | **Foundational — Phase‑0 Spike A gates everything.** If it fails on a device, that device gets the VM/qemu‑user path, not this engine. |
| 2 | 🔴 **SUD absent (kernel < 5.11)** | `PR_SET_SYSCALL_USER_DISPATCH` needs ≥ 5.11 (Android 5.15 GKI ok; 5.10/5.4 not) | Raw‑syscall interception (§3.9) unavailable → fall back to seccomp‑unotify (FS/exec/cred subset) or **qemu‑user**. Detect kernel at runtime. |
| 3 | 🟠 **Static binaries / raw `svc #0`** (Go, static musl) | bypass the LD_PRELOAD fast path | **Closed by SUD (§3.9)** where kernel ≥ 5.11; otherwise falls to #2's fallback. |
| 4 | 🟠 **glibc `ld.so` complexity** (IFUNC, TLS models, symbol versioning, RELRO) | full dynamic‑link semantics | De‑risked two ways: **musl‑first** (Phase‑0 Spike B), and **routing library mmaps to `nativeLibraryDir`** (§3.2 / §3.11) so stock `ld.so` may need no patching. Spike C proves glibc. |
| 5 | 🟠 **User‑installed `.so` in the writable rootfs** (`apt install libfoo`) | those libs are `app_data_file` → file‑backed `PROT_EXEC` still blocked; the nativeLibraryDir trick doesn't cover them | The reflective loader (§3.1) must anon‑map *these* libs too, not just the main binary. Prebundling only covers the known core. |
| 6 | 🟠 **Debuggers (`gdb`/`strace`), namespaces, `unshare`, containers‑in‑guest (Docker)** | need real `ptrace`, cgroups, kernel namespaces | Not doable in a rootless loader; partial `/proc` shims only. **Full fidelity is VM‑only** — document as out of scope for the engine. |
| 7 | 🟠 **Fixed‑base `ET_EXEC` colliding with the stub** | non‑PIE binary demands an address range the loader occupies | Rare (modern toolchains emit PIE `ET_DYN`). Needs a relocated stub layout for the few offenders. |
| 8 | 🟢 **Higher RAM per process** | anon‑copy loading loses file‑backed page sharing — each process gets a private `libc` instead of shared read‑only pages | Mitigate by backing prebundled libs from `nativeLibraryDir` (real file maps, shared) wherever possible; accept the rest. |
| 9 | 🟢 **Syscall tax on syscall‑heavy phases** | one `SIGSYS` per guest syscall under SUD | Compute stays native; only crossings pay. Far cheaper than ptrace; `svc`‑rewriting (§3.9 #3) available if a hot path bites. |
| 10 | 🟢 **exec edge cases** — `vfork`, `posix_spawn`, multi‑threaded exec, signal/`wait` semantics | correct re‑entry into the engine across process boundaries | Incremental hardening in `exec_router` (§3.4); covered by the Phase‑2 `make`/`npm` workloads. |

**Correctness safety net — per channel (they differ):**

- **F‑Droid build (`targetSdk ≤ 28`):** classic **proot** is the proven baseline
  and a valid fallback — legacy semantics allow exec from app storage. Use the
  **maintained [`termux/proot`](https://github.com/termux/proot)** (GPLv2), **not**
  the stale `CypherpunkArmory/proot` fork (6 years old, 169 commits behind). Ship
  on proot today; this engine only *adds* speed.
- **Play build (`targetSdk ≥ 29`):** **proot is _not_ a valid fallback** here — it
  `execve`s guest binaries in app storage, the exact operation SELinux blocks. The
  only fallbacks that work are **this engine** and **qemu‑user** (both load
  binaries as *data* and never file‑exec). Anywhere the engine can't yet cope,
  fall back to **qemu‑user**, not proot.

---

## 7. Legal / licensing

**Principle:** ideas and functional concepts are not copyrightable (idea–expression
dichotomy). We may reimplement *what* any tool does — we may not copy *how it is
written* (its expression). Two distinct risks govern how we gather those ideas:

- **Copyright** — reimplementing behavior is fine; copying code/structure is not.
- **Contract/license** — proprietary licenses (e.g. proroot's) may forbid reverse
  engineering as a *contract term*, binding **independently** of copyright. Assume
  a proprietary "no redistribution of modified binaries" license is hostile to RE.
- **Contamination** — once an implementer has seen a proprietary disassembly,
  proving independent creation gets hard. Clean‑room separation is our *evidence*.

**Rules for this project:**
1. **Derive from open sources, which we may read and adapt freely:** the ELF spec,
   **[`anvilsecure/ulexecve`](https://github.com/anvilsecure/ulexecve)** (BSD‑3 —
   the userland‑exec port source, freely reusable **with attribution**), **musl**
   dynamic linker (MIT — simplest correct linker reference), **[`termux/proot`](https://github.com/termux/proot)**
   (GPLv2 — the maintained proot for the `≤ 28` fallback; its `loader.c` is the
   interpreter‑hand‑off reference), **glibc** `ld.so` (LGPL). Everything the
   technique needs is here; we do **not** need any proprietary binary.
2. **Black‑box behavioral observation is allowed and encouraged** — `strace`/
   `ltrace`, syscall/`/proc` interception traces, timing. These are *functional
   facts*, safe to document and reimplement.
3. **Do not statically disassemble proprietary binaries** (low upside — the ideas
   are already in open code; real downside — license breach + contamination).
   Disassemble freely only what is **FOSS or our own**.
4. **Keep a written source log** for each module (which open reference / observed
   behavior it derived from). This record is our proof of independent creation.
5. If **proot** source is linked/derived, that component is **GPLv2** — keep it a
   separable module so overall licensing stays clean.

The runtime is **on‑device only** → consistent with the product thesis. It is a
*local* dependency and does not reintroduce a server.
```
