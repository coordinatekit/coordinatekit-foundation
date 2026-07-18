FROM eclipse-temurin:21-jdk-jammy

ARG TZ=UTC
ENV TZ="$TZ"

# Layer 1: Base development tools (most stable, rarely changes)
RUN apt-get update && apt-get install -y --no-install-recommends \
  curl \
  git \
  gnupg2 \
  jq \
  less \
  nano \
  procps \
  sudo \
  unzip \
  vim \
  zsh \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# Layer 2: Agent tools
RUN apt-get update && apt-get install -y --no-install-recommends \
  pipx \
  python3 \
  python3-pip \
  socat \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# Create non-root user with passwordless sudo
ARG USERNAME=developer
ARG USER_UID=1000
ARG USER_GID=$USER_UID
RUN groupadd --gid $USER_GID $USERNAME \
  && useradd --uid $USER_UID --gid $USER_GID -m -s /bin/zsh $USERNAME \
  && echo "$USERNAME ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/$USERNAME \
  && chmod 0440 /etc/sudoers.d/$USERNAME

# Claude Code managed settings
RUN mkdir -p /etc/claude-code && cat > /etc/claude-code/managed-settings.json <<'EOF'
{
  "permissions": {
    "deny": [
      "Read(~/.ssh/**)",
      "Read(~/.gnupg/**)",
      "Read(~/.git-credentials)",
      "Read(~/.npmrc)",
      "Read(~/Library/Keychains/**)",
      "Edit(~/.ssh/**)",
      "Edit(~/.zshrc)",
      "Edit(~/.bashrc)",
      "Bash(sudo *)",
      "Bash(rm -rf *)"
    ]
  },
  "sandbox": {
    "allowUnsandboxedCommands": true,
    "enabled": true,
    "network": {
      "allowedDomains": [
        "github.com",
        "api.github.com",
        "codeload.github.com",
        "github.githubassets.com",
        "objects.githubusercontent.com",
        "raw.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "api.anthropic.com",
        "platform.claude.com",
        "statsig.anthropic.com",
        "pypi.org",
        "files.pythonhosted.org",
        "repo1.maven.org",
        "plugins.gradle.org",
        "services.gradle.org"
      ]
    }
  }
}
EOF

# Set up persistent shell history directory (mounted as a volume)
RUN mkdir -p /command-history \
  && chown $USERNAME:$USERNAME /command-history

# Switch to non-root user for remaining installs
USER $USERNAME
ENV PATH="/home/$USERNAME/.local/bin:$PATH"

# Install Claude Code via native installer
RUN curl -fsSL https://claude.ai/install.sh | bash

# Install pre-commit via pipx
RUN pipx install pre-commit

# Set environment
ENV DEVCONTAINER=true
ENV SHELL=/bin/zsh
ENV EDITOR=nano
ENV VISUAL=nano
ENV HISTFILE=/command-history/.zsh_history
ENV COLORTERM=truecolor
