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

