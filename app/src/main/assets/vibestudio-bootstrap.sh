#!/system/bin/sh
set -ex

echo "[vibestudio-bootstrap] Starting environment setup..."
export PATH="$PREFIX/bin:$PREFIX/bin/applets:/system/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib:$LD_LIBRARY_PATH"
export TMPDIR="$PREFIX/tmp"
export TERM="xterm-256color"
export TERMUX_PKG_NO_MIRROR_SELECT="true"
export DPKG_ADMINDIR="$PREFIX/var/lib/dpkg"
export APT_CONFIG="$PREFIX/etc/apt/apt.conf"

echo "[vibestudio-bootstrap] PREFIX=$PREFIX"
echo "[vibestudio-bootstrap] HOME=$HOME"
echo "[vibestudio-bootstrap] PATH=$PATH"
echo "[vibestudio-bootstrap] LD_LIBRARY_PATH=$LD_LIBRARY_PATH"
echo "[vibestudio-bootstrap] DPKG_ADMINDIR=$DPKG_ADMINDIR"

chmod -R 755 "$PREFIX/bin" "$PREFIX/libexec" "$PREFIX/lib/apt/methods" 2>/dev/null || true

mkdir -p "$PREFIX/etc/dpkg/dpkg.cfg.d" "$PREFIX/var/lib/dpkg/updates" "$PREFIX/var/lib/dpkg/info" "$PREFIX/var/lib/dpkg/triggers" "$PREFIX/var/lib/dpkg/alternatives" "$PREFIX/tmp"
touch "$PREFIX/var/lib/dpkg/status" "$PREFIX/var/lib/dpkg/available"

# Patch pkg script if it contains hardcoded /data/data/com.termux/files/usr
if [ -x "$PREFIX/bin/pkg" ]; then
    sed -i "s|/data/data/com.termux/files/usr|$PREFIX|g" "$PREFIX/bin/pkg" 2>/dev/null || true
fi

# Neutralize termux-bootstrap second-stage triggers completely and silently
if [ -d "$PREFIX/etc/termux/termux-bootstrap" ]; then
    mkdir -p "$PREFIX/etc/termux/termux-bootstrap/second-stage"
    echo "#!/system/bin/sh" > "$PREFIX/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
    echo "exit 0" >> "$PREFIX/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
    chmod 755 "$PREFIX/etc/termux/termux-bootstrap/second-stage/termux-bootstrap-second-stage.sh"
fi

rm -f "$PREFIX/etc/profile.d/termux-bootstrap.sh" 2>/dev/null || true

# Strip any fallback run log lines from profile scripts
if [ -f "$PREFIX/etc/profile" ]; then
    sed -i "/fallback run/d" "$PREFIX/etc/profile" 2>/dev/null || true
    sed -i "/termux-bootstrap/d" "$PREFIX/etc/profile" 2>/dev/null || true
fi
if [ -f "$PREFIX/etc/bash.bashrc" ]; then
    sed -i "/fallback run/d" "$PREFIX/etc/bash.bashrc" 2>/dev/null || true
    sed -i "/termux-bootstrap/d" "$PREFIX/etc/bash.bashrc" 2>/dev/null || true
fi

echo "[vibestudio-bootstrap] Checking available package managers..."
# Link default mirror to chosen_mirrors
if [ -f "$PREFIX/etc/termux/mirrors/default" ]; then
    mkdir -p "$PREFIX/etc/termux"
    rm -f "$PREFIX/etc/termux/chosen_mirrors"
    ln -sf "$PREFIX/etc/termux/mirrors/default" "$PREFIX/etc/termux/chosen_mirrors"
fi

if [ -x "$PREFIX/bin/apt-get" ]; then
    echo "[vibestudio-bootstrap] Found apt-get at $PREFIX/bin/apt-get"
    echo "[vibestudio-bootstrap] Running apt-get update..."
    "$PREFIX/bin/apt-get" update -y
    echo "[vibestudio-bootstrap] Installing ca-certificates termux-keyring..."
    "$PREFIX/bin/apt-get" install -y ca-certificates termux-keyring
    echo "[vibestudio-bootstrap] Running package upgrades (dist-upgrade)..."
    "$PREFIX/bin/apt-get" dist-upgrade -y -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" || "$PREFIX/bin/apt-get" upgrade -y
elif [ -x "$PREFIX/bin/pkg" ] && [ -x "$PREFIX/bin/bash" ]; then
    echo "[vibestudio-bootstrap] Found pkg at $PREFIX/bin/pkg"
    echo "[vibestudio-bootstrap] Running pkg update..."
    "$PREFIX/bin/bash" "$PREFIX/bin/pkg" update -y
    echo "[vibestudio-bootstrap] Installing ca-certificates termux-keyring..."
    "$PREFIX/bin/bash" "$PREFIX/bin/pkg" install -y ca-certificates termux-keyring
    echo "[vibestudio-bootstrap] Running pkg upgrade..."
    "$PREFIX/bin/bash" "$PREFIX/bin/pkg" upgrade -y
else
    echo "[vibestudio-bootstrap] Error: Neither apt-get nor pkg found at $PREFIX/bin"
    exit 1
fi

echo "[vibestudio-bootstrap] Environment setup completed!"
