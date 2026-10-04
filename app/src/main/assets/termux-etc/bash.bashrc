# System-wide .bashrc file for interactive bash(1) shells.

# Command history settings
HISTFILESIZE=2000
HISTSIZE=1000
HISTCONTROL=ignoreboth

# Default prompt
PS1='\[\033[01;32m\]\u@termux\[\033[00m\]:\[\033[01;34m\]\w\[\033[00m\]\$ '

# Display MOTD once at session startup
if [ -f "$PREFIX/etc/motd" ] && [ -z "$MOTD_SHOWN" ]; then
    export MOTD_SHOWN=1
    cat "$PREFIX/etc/motd"
fi

# Useful aliases
alias ls='ls --color=auto'
alias ll='ls -la'
alias grep='grep --color=auto'

# Edit command to open files in Editor tab via MainActivity
edit() {
    if [ -z "$1" ]; then
        echo "[EDIT_CMD] Error: No file specified."
        echo "Usage: edit <file_path>"
        return 1
    fi
    local file_path
    if [[ "$1" = /* ]]; then
        file_path="$1"
    else
        file_path="$(pwd)/$1"
    fi
    echo "[EDIT_CMD] Before sending intent: Requesting to edit file: $file_path"
    log -p i -t EditCmd "Before throwing intent to MainActivity for file: $file_path"
    am start -n com.vibestudio.app/.activity.MainActivity --es file_path "$file_path" -f 0x20000000
    echo "[EDIT_CMD] Intent sent successfully for file: $file_path"
}
